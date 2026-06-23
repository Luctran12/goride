package com.example.goride.payment.service;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.booking.domain.PricingConfig;
import com.example.goride.booking.domain.Trip;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.driver.domain.VehicleType;
import com.example.goride.payment.config.PaymentProviderProperties;
import com.example.goride.payment.domain.Payment;
import com.example.goride.payment.domain.PaymentStatus;
import com.example.goride.payment.provider.MoMoPaymentClient;
import com.example.goride.payment.provider.MoMoPaymentProvider;
import com.example.goride.payment.provider.PaymentProviderRegistry;
import com.example.goride.payment.provider.PaymentWebhookFreshnessPolicy;
import com.example.goride.payment.provider.VnPayPaymentProvider;
import com.example.goride.payment.repository.PaymentRepository;
import com.example.goride.user.domain.User;
import com.example.goride.user.domain.UserRole;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentSandboxWebhookContractTests {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final Instant FIXED_NOW = Instant.parse("2026-06-13T06:00:00Z");
    private static final ZoneId VNPAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNPAY_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(VNPAY_ZONE);

    @Test
    void acceptsMomoSandboxSuccessCallbackThroughWebhookService() {
        Payment payment = payment(PaymentMethod.MOMO, BigDecimal.valueOf(20000));
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentCompletionWorkflow completionWorkflow = mock(PaymentCompletionWorkflow.class);
        when(paymentRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PaymentWebhookService service = service(paymentRepository, completionWorkflow);

        var response = service.handleProviderWebhook(
                "momo",
                Map.of("content-type", "application/json"),
                momoIpnPayload(0, 4088878653L, FIXED_NOW.minusSeconds(30))
        );

        assertThat(response.provider()).isEqualTo("momo");
        assertThat(response.accepted()).isTrue();
        assertThat(response.paymentId()).isEqualTo(70L);
        assertThat(response.tripId()).isEqualTo(99L);
        assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(response.transactionRef()).isEqualTo("4088878653");
        verify(completionWorkflow).handleCompletedPayment(payment);
    }

    @Test
    void acceptsVnPaySandboxFailureCallbackThroughWebhookService() {
        Payment payment = payment(PaymentMethod.VNPAY, BigDecimal.valueOf(20000));
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentCompletionWorkflow completionWorkflow = mock(PaymentCompletionWorkflow.class);
        when(paymentRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PaymentWebhookService service = service(paymentRepository, completionWorkflow);

        var response = service.handleProviderWebhook(
                "vnpay",
                Map.of(),
                vnpayWebhookPayload("24", "02", "14123457", "2000000", FIXED_NOW.minusSeconds(20))
        );

        assertThat(response.provider()).isEqualTo("vnpay");
        assertThat(response.accepted()).isTrue();
        assertThat(response.paymentId()).isEqualTo(70L);
        assertThat(response.tripId()).isEqualTo(99L);
        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.transactionRef()).isEqualTo("14123457");
        verify(completionWorkflow, never()).handleCompletedPayment(any(Payment.class));
    }

    @Test
    void rejectsStaleSandboxCallbackUsingServiceReceivedAtClock() {
        Payment payment = payment(PaymentMethod.VNPAY, BigDecimal.valueOf(20000));
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        when(paymentRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(payment));
        PaymentWebhookService service = service(paymentRepository, mock(PaymentCompletionWorkflow.class));

        assertThatThrownBy(() -> service.handleProviderWebhook(
                "vnpay",
                Map.of(),
                vnpayWebhookPayload("00", "00", "14123456", "2000000", FIXED_NOW.minusSeconds(86_401))
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR)
                )
                .hasMessage("Payment provider callback is too old");

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    private PaymentWebhookService service(
            PaymentRepository paymentRepository,
            PaymentCompletionWorkflow completionWorkflow
    ) {
        PaymentProviderProperties properties = properties();
        PaymentWebhookFreshnessPolicy freshnessPolicy = new PaymentWebhookFreshnessPolicy();
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        return new PaymentWebhookService(
                new PaymentProviderRegistry(List.of(
                        new MoMoPaymentProvider(
                                properties,
                                mock(MoMoPaymentClient.class),
                                paymentRepository,
                                completionWorkflow,
                                freshnessPolicy
                        ),
                        new VnPayPaymentProvider(
                                properties,
                                fixedClock,
                                paymentRepository,
                                completionWorkflow,
                                freshnessPolicy
                        )
                )),
                fixedClock
        );
    }

    private PaymentProviderProperties properties() {
        PaymentProviderProperties properties = new PaymentProviderProperties();
        PaymentProviderProperties.ProviderSettings momo = properties.getMomo();
        momo.setEnabled(true);
        momo.setMerchantId("GORIDE");
        momo.setAccessKey("momo-access");
        momo.setSecretKey("momo-secret");
        momo.setCheckoutBaseUrl("https://test-payment.momo.vn/v2/gateway/api/create");
        momo.setReturnUrl("https://app.goride.test/payments/momo/return");
        momo.setIpnUrl("https://api.goride.test/api/v1/payments/providers/momo/webhook");

        PaymentProviderProperties.ProviderSettings vnpay = properties.getVnpay();
        vnpay.setEnabled(true);
        vnpay.setMerchantId("GORIDETMN");
        vnpay.setSecretKey("vnp-secret");
        vnpay.setCheckoutBaseUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        vnpay.setReturnUrl("https://api.goride.test/payments/vnpay/return");
        vnpay.setDefaultIpAddress("203.0.113.10");
        vnpay.setWebhookSecret("vnp-secret");
        return properties;
    }

    private Map<String, Object> momoIpnPayload(
            int resultCode,
            long transId,
            Instant responseTime
    ) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("partnerCode", "GORIDE");
        payload.put("orderId", "GORIDE-PAY-70");
        payload.put("requestId", "GORIDE-CREATE-70");
        payload.put("amount", 20000L);
        payload.put("orderInfo", "GoRide trip 99 payment 70");
        payload.put("orderType", "momo_wallet");
        payload.put("transId", transId);
        payload.put("resultCode", resultCode);
        payload.put("message", resultCode == 0 ? "Successful." : "Transaction denied");
        payload.put("payType", "qr");
        payload.put("responseTime", responseTime.toEpochMilli());
        payload.put("extraData", "");
        payload.put("signature", momoNotificationSignature(payload));
        return payload;
    }

    private String momoNotificationSignature(Map<String, Object> payload) {
        String rawSignature = "accessKey=momo-access"
                + "&amount=" + payload.get("amount")
                + "&extraData=" + payload.get("extraData")
                + "&message=" + payload.get("message")
                + "&orderId=" + payload.get("orderId")
                + "&orderInfo=" + payload.get("orderInfo")
                + "&orderType=" + payload.get("orderType")
                + "&partnerCode=" + payload.get("partnerCode")
                + "&payType=" + payload.get("payType")
                + "&requestId=" + payload.get("requestId")
                + "&responseTime=" + payload.get("responseTime")
                + "&resultCode=" + payload.get("resultCode")
                + "&transId=" + payload.get("transId");
        return hmacSha256("momo-secret", rawSignature);
    }

    private Map<String, Object> vnpayWebhookPayload(
            String responseCode,
            String transactionStatus,
            String transactionNo,
            String amount,
            Instant payDate
    ) {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Amount", amount);
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_CardType", "ATM");
        params.put("vnp_OrderInfo", "GoRide trip 99 payment 70");
        params.put("vnp_PayDate", VNPAY_TIME_FORMAT.format(payDate));
        params.put("vnp_ResponseCode", responseCode);
        params.put("vnp_TmnCode", "GORIDETMN");
        params.put("vnp_TransactionNo", transactionNo);
        params.put("vnp_TransactionStatus", transactionStatus);
        params.put("vnp_TxnRef", "GORIDE-PAY-70");
        params.put("vnp_SecureHash", expectedVnPaySecureHash(params));
        Map<String, Object> payload = new TreeMap<>();
        payload.putAll(params);
        return payload;
    }

    private String expectedVnPaySecureHash(Map<String, String> params) {
        StringBuilder hashData = new StringBuilder();
        params.entrySet().stream()
                .filter(entry -> !"vnp_SecureHash".equals(entry.getKey()))
                .filter(entry -> !"vnp_SecureHashType".equals(entry.getKey()))
                .forEach(entry -> {
                    if (!hashData.isEmpty()) {
                        hashData.append('&');
                    }
                    hashData.append(urlEncode(entry.getKey()))
                            .append('=')
                            .append(urlEncode(entry.getValue()));
                });
        return hmacSha512("vnp-secret", hashData.toString());
    }

    private Payment payment(PaymentMethod method, BigDecimal amount) {
        Trip trip = Trip.create(
                user(10L, UserRole.PASSENGER),
                VehicleType.MOTORBIKE,
                method,
                "Ben Thanh Market",
                point(106.7000, 10.7700),
                "Tan Son Nhat Airport",
                point(106.6650, 10.8130),
                BigDecimal.valueOf(4.2),
                18,
                BigDecimal.valueOf(32000),
                pricingConfig()
        );
        ReflectionTestUtils.setField(trip, "id", 99L);
        trip.accept(user(20L, UserRole.DRIVER));
        trip.markArrived();
        trip.startTrip();
        trip.complete(amount, BigDecimal.valueOf(4.2), 18);

        Payment payment = Payment.createPending(trip);
        ReflectionTestUtils.setField(payment, "id", 70L);
        return payment;
    }

    private PricingConfig pricingConfig() {
        return PricingConfig.create(
                VehicleType.MOTORBIKE,
                BigDecimal.valueOf(10000),
                BigDecimal.valueOf(4000),
                BigDecimal.valueOf(300),
                BigDecimal.valueOf(15000),
                BigDecimal.ONE,
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }

    private User user(Long id, UserRole role) {
        User user = User.create(role.name(), "09000000" + id, null, "hash", Set.of(role));
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private org.locationtech.jts.geom.Point point(double longitude, double latitude) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }

    private String hmacSha256(String secretKey, String data) {
        return hmac("HmacSHA256", secretKey, data);
    }

    private String hmacSha512(String secretKey, String data) {
        return hmac("HmacSHA512", secretKey, data);
    }

    private String hmac(String algorithm, String secretKey, String data) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), algorithm));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
