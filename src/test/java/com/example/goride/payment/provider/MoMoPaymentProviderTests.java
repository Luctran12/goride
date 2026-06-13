package com.example.goride.payment.provider;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.booking.domain.PricingConfig;
import com.example.goride.booking.domain.Trip;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.driver.domain.VehicleType;
import com.example.goride.payment.config.PaymentProviderProperties;
import com.example.goride.payment.domain.Payment;
import com.example.goride.payment.domain.PaymentStatus;
import com.example.goride.payment.repository.PaymentRepository;
import com.example.goride.payment.service.PaymentCompletionWorkflow;
import com.example.goride.user.domain.User;
import com.example.goride.user.domain.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MoMoPaymentProviderTests {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final String CHECKOUT_URL = "https://test-payment.momo.vn/v2/gateway/api/create";
    private static final String PAY_URL = "https://test-payment.momo.vn/v2/gateway/pay?t=token";

    @Test
    void createsSignedCheckoutRequestAndReturnsPayUrl() {
        PaymentProviderProperties properties = properties();
        MoMoPaymentClient paymentClient = mock(MoMoPaymentClient.class);
        when(paymentClient.createPayment(eq(CHECKOUT_URL), any(MoMoCreatePaymentRequest.class)))
                .thenAnswer(invocation -> successfulResponse(invocation.getArgument(1), PAY_URL));
        MoMoPaymentProvider provider = provider(properties, paymentClient);

        PaymentCheckoutSession session = provider.createCheckoutSession(payment(BigDecimal.valueOf(20000)));

        assertThat(session.checkoutRequired()).isTrue();
        assertThat(session.checkoutUrl()).isEqualTo(PAY_URL);
        assertThat(session.expiresAt()).isNull();

        ArgumentCaptor<MoMoCreatePaymentRequest> requestCaptor =
                ArgumentCaptor.forClass(MoMoCreatePaymentRequest.class);
        verify(paymentClient).createPayment(eq(CHECKOUT_URL), requestCaptor.capture());
        MoMoCreatePaymentRequest request = requestCaptor.getValue();
        assertThat(request.partnerCode()).isEqualTo("GORIDE");
        assertThat(request.requestType()).isEqualTo("captureWallet");
        assertThat(request.ipnUrl()).isEqualTo(
                "https://api.goride.test/api/v1/payments/providers/momo/webhook"
        );
        assertThat(request.redirectUrl()).isEqualTo("https://app.goride.test/payments/momo/return");
        assertThat(request.orderId()).isEqualTo("GORIDE-PAY-70");
        assertThat(request.requestId()).isEqualTo("GORIDE-CREATE-70");
        assertThat(request.amount()).isEqualTo(20000);
        assertThat(request.orderInfo()).isEqualTo("GoRide trip 99 payment 70");
        assertThat(request.extraData()).isEmpty();
        assertThat(request.lang()).isEqualTo("vi");
        assertThat(request.signature()).isEqualTo(expectedRequestSignature(request, "momo-secret"));
    }

    @Test
    void reusesStableOrderAndRequestIdsForRepeatedCheckout() {
        MoMoPaymentClient paymentClient = mock(MoMoPaymentClient.class);
        when(paymentClient.createPayment(eq(CHECKOUT_URL), any(MoMoCreatePaymentRequest.class)))
                .thenAnswer(invocation -> successfulResponse(invocation.getArgument(1), PAY_URL));
        MoMoPaymentProvider provider = provider(properties(), paymentClient);
        Payment payment = payment(BigDecimal.valueOf(20000));

        provider.createCheckoutSession(payment);
        provider.createCheckoutSession(payment);

        ArgumentCaptor<MoMoCreatePaymentRequest> requestCaptor =
                ArgumentCaptor.forClass(MoMoCreatePaymentRequest.class);
        verify(paymentClient, times(2)).createPayment(eq(CHECKOUT_URL), requestCaptor.capture());
        assertThat(requestCaptor.getAllValues())
                .extracting(MoMoCreatePaymentRequest::orderId)
                .containsOnly("GORIDE-PAY-70");
        assertThat(requestCaptor.getAllValues())
                .extracting(MoMoCreatePaymentRequest::requestId)
                .containsOnly("GORIDE-CREATE-70");
    }

    @Test
    void rejectsCheckoutWhenProviderIsNotConfigured() {
        MoMoPaymentClient paymentClient = mock(MoMoPaymentClient.class);
        MoMoPaymentProvider provider = provider(new PaymentProviderProperties(), paymentClient);

        assertThatThrownBy(() -> provider.createCheckoutSession(payment(BigDecimal.valueOf(20000))))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.PAYMENT_PROVIDER_UNSUPPORTED)
                );
        verify(paymentClient, never()).createPayment(any(), any());
    }

    @Test
    void rejectsAmountOutsideMomoLimits() {
        MoMoPaymentClient paymentClient = mock(MoMoPaymentClient.class);
        MoMoPaymentProvider provider = provider(properties(), paymentClient);

        assertProviderError(() -> provider.createCheckoutSession(payment(BigDecimal.valueOf(999))));
        assertProviderError(() -> provider.createCheckoutSession(payment(BigDecimal.valueOf(50_000_001))));
        verify(paymentClient, never()).createPayment(any(), any());
    }

    @Test
    void rejectsResponseWithInvalidSignature() {
        MoMoPaymentClient paymentClient = mock(MoMoPaymentClient.class);
        when(paymentClient.createPayment(eq(CHECKOUT_URL), any(MoMoCreatePaymentRequest.class)))
                .thenAnswer(invocation -> {
                    MoMoCreatePaymentResponse response =
                            successfulResponse(invocation.getArgument(1), PAY_URL);
                    return responseWith(response, response.amount(), response.resultCode(), PAY_URL, "invalid");
                });
        MoMoPaymentProvider provider = provider(properties(), paymentClient);

        assertProviderError(() -> provider.createCheckoutSession(payment(BigDecimal.valueOf(20000))));
    }

    @Test
    void rejectsResponseThatDoesNotMatchRequest() {
        MoMoPaymentClient paymentClient = mock(MoMoPaymentClient.class);
        when(paymentClient.createPayment(eq(CHECKOUT_URL), any(MoMoCreatePaymentRequest.class)))
                .thenAnswer(invocation -> {
                    MoMoCreatePaymentRequest request = invocation.getArgument(1);
                    return signedResponse(
                            request.partnerCode(),
                            request.requestId(),
                            request.orderId(),
                            request.amount() + 1000,
                            0,
                            PAY_URL
                    );
                });
        MoMoPaymentProvider provider = provider(properties(), paymentClient);

        assertProviderError(() -> provider.createCheckoutSession(payment(BigDecimal.valueOf(20000))));
    }

    @Test
    void rejectsProviderFailureResultCode() {
        MoMoPaymentClient paymentClient = mock(MoMoPaymentClient.class);
        when(paymentClient.createPayment(eq(CHECKOUT_URL), any(MoMoCreatePaymentRequest.class)))
                .thenAnswer(invocation -> {
                    MoMoCreatePaymentRequest request = invocation.getArgument(1);
                    return signedResponse(
                            request.partnerCode(),
                            request.requestId(),
                            request.orderId(),
                            request.amount(),
                            1006,
                            ""
                    );
                });
        MoMoPaymentProvider provider = provider(properties(), paymentClient);

        assertProviderError(() -> provider.createCheckoutSession(payment(BigDecimal.valueOf(20000))));
    }

    @Test
    void rejectsSuccessResponseWithoutPayUrl() {
        MoMoPaymentClient paymentClient = mock(MoMoPaymentClient.class);
        when(paymentClient.createPayment(eq(CHECKOUT_URL), any(MoMoCreatePaymentRequest.class)))
                .thenAnswer(invocation -> {
                    MoMoCreatePaymentRequest request = invocation.getArgument(1);
                    return signedResponse(
                            request.partnerCode(),
                            request.requestId(),
                            request.orderId(),
                            request.amount(),
                            0,
                            ""
                    );
                });
        MoMoPaymentProvider provider = provider(properties(), paymentClient);

        assertProviderError(() -> provider.createCheckoutSession(payment(BigDecimal.valueOf(20000))));
    }

    @Test
    void handlesSuccessfulIpnAndRunsCompletionWorkflow() {
        Payment payment = payment(BigDecimal.valueOf(20000));
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentCompletionWorkflow paymentCompletionWorkflow = mock(PaymentCompletionWorkflow.class);
        when(paymentRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        MoMoPaymentProvider provider = provider(
                properties(),
                mock(MoMoPaymentClient.class),
                paymentRepository,
                paymentCompletionWorkflow
        );

        PaymentWebhookResult result = provider.handleWebhook(webhookRequest(ipnPayload(0, 4088878653L)));

        assertThat(result.accepted()).isTrue();
        assertThat(result.paymentId()).isEqualTo(70L);
        assertThat(result.tripId()).isEqualTo(99L);
        assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(result.transactionRef()).isEqualTo("4088878653");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getProvider()).isEqualTo("momo");
        verify(paymentCompletionWorkflow).handleCompletedPayment(payment);
    }

    @Test
    void acceptsDuplicateSuccessfulIpnWithoutRunningCompletionWorkflowAgain() {
        Payment payment = payment(BigDecimal.valueOf(20000));
        payment.markCompletedByProvider("momo", "4088878653");
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentCompletionWorkflow paymentCompletionWorkflow = mock(PaymentCompletionWorkflow.class);
        when(paymentRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        MoMoPaymentProvider provider = provider(
                properties(),
                mock(MoMoPaymentClient.class),
                paymentRepository,
                paymentCompletionWorkflow
        );

        PaymentWebhookResult result = provider.handleWebhook(webhookRequest(ipnPayload(0, 4088878653L)));

        assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED);
        verify(paymentCompletionWorkflow, never()).handleCompletedPayment(any(Payment.class));
    }

    @Test
    void handlesFailedIpnWithoutCompletionWorkflow() {
        Payment payment = payment(BigDecimal.valueOf(20000));
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentCompletionWorkflow paymentCompletionWorkflow = mock(PaymentCompletionWorkflow.class);
        when(paymentRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        MoMoPaymentProvider provider = provider(
                properties(),
                mock(MoMoPaymentClient.class),
                paymentRepository,
                paymentCompletionWorkflow
        );

        PaymentWebhookResult result = provider.handleWebhook(webhookRequest(ipnPayload(1006, 4088878654L)));

        assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.transactionRef()).isEqualTo("4088878654");
        verify(paymentCompletionWorkflow, never()).handleCompletedPayment(any(Payment.class));
    }

    @Test
    void treatsAuthorizedIpnAsSuccessfulForAutoCaptureCheckout() {
        Payment payment = payment(BigDecimal.valueOf(20000));
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentCompletionWorkflow paymentCompletionWorkflow = mock(PaymentCompletionWorkflow.class);
        when(paymentRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        MoMoPaymentProvider provider = provider(
                properties(),
                mock(MoMoPaymentClient.class),
                paymentRepository,
                paymentCompletionWorkflow
        );

        PaymentWebhookResult result = provider.handleWebhook(webhookRequest(ipnPayload(9000, 4088878655L)));

        assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED);
        verify(paymentCompletionWorkflow).handleCompletedPayment(payment);
    }

    @Test
    void rejectsInvalidIpnSignatureBeforeLoadingPayment() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        MoMoPaymentProvider provider = provider(
                properties(),
                mock(MoMoPaymentClient.class),
                paymentRepository,
                mock(PaymentCompletionWorkflow.class)
        );
        Map<String, Object> payload = ipnPayload(0, 4088878653L);
        payload.put("signature", "invalid");

        assertValidationError(() -> provider.handleWebhook(webhookRequest(payload)));

        verify(paymentRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void acceptsUppercaseIpnSignature() {
        Payment payment = payment(BigDecimal.valueOf(20000));
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        when(paymentRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        MoMoPaymentProvider provider = provider(
                properties(),
                mock(MoMoPaymentClient.class),
                paymentRepository,
                mock(PaymentCompletionWorkflow.class)
        );
        Map<String, Object> payload = ipnPayload(0, 4088878653L);
        payload.computeIfPresent(
                "signature",
                (key, value) -> value.toString().toUpperCase(Locale.ROOT)
        );

        PaymentWebhookResult result = provider.handleWebhook(webhookRequest(payload));

        assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "partnerCode",
            "orderId",
            "requestId",
            "amount",
            "orderInfo",
            "extraData"
    })
    void rejectsIpnDataThatDoesNotMatchPayment(String mismatchedField) {
        Payment payment = payment(BigDecimal.valueOf(20000));
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        when(paymentRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(payment));
        MoMoPaymentProvider provider = provider(
                properties(),
                mock(MoMoPaymentClient.class),
                paymentRepository,
                mock(PaymentCompletionWorkflow.class)
        );
        Map<String, Object> payload = ipnPayload(0, 4088878653L);
        payload.put(mismatchedField, mismatchedValue(mismatchedField));
        payload.put("signature", notificationSignature(payload));

        assertValidationError(() -> provider.handleWebhook(webhookRequest(payload)));

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    private PaymentProviderProperties properties() {
        PaymentProviderProperties properties = new PaymentProviderProperties();
        PaymentProviderProperties.ProviderSettings momo = properties.getMomo();
        momo.setEnabled(true);
        momo.setMerchantId("GORIDE");
        momo.setAccessKey("momo-access");
        momo.setSecretKey("momo-secret");
        momo.setCheckoutBaseUrl(CHECKOUT_URL);
        momo.setReturnUrl("https://app.goride.test/payments/momo/return");
        momo.setIpnUrl("https://api.goride.test/api/v1/payments/providers/momo/webhook");
        return properties;
    }

    private MoMoPaymentProvider provider(
            PaymentProviderProperties properties,
            MoMoPaymentClient paymentClient
    ) {
        return provider(
                properties,
                paymentClient,
                mock(PaymentRepository.class),
                mock(PaymentCompletionWorkflow.class)
        );
    }

    private MoMoPaymentProvider provider(
            PaymentProviderProperties properties,
            MoMoPaymentClient paymentClient,
            PaymentRepository paymentRepository,
            PaymentCompletionWorkflow paymentCompletionWorkflow
    ) {
        return new MoMoPaymentProvider(
                properties,
                paymentClient,
                paymentRepository,
                paymentCompletionWorkflow
        );
    }

    private MoMoCreatePaymentResponse successfulResponse(
            MoMoCreatePaymentRequest request,
            String payUrl
    ) {
        return signedResponse(
                request.partnerCode(),
                request.requestId(),
                request.orderId(),
                request.amount(),
                0,
                payUrl
        );
    }

    private MoMoCreatePaymentResponse signedResponse(
            String partnerCode,
            String requestId,
            String orderId,
            long amount,
            int resultCode,
            String payUrl
    ) {
        long responseTime = 1_781_300_000_000L;
        String rawSignature = "accessKey=momo-access"
                + "&amount=" + amount
                + "&orderId=" + orderId
                + "&partnerCode=" + partnerCode
                + "&payUrl=" + payUrl
                + "&requestId=" + requestId
                + "&responseTime=" + responseTime
                + "&resultCode=" + resultCode;
        return new MoMoCreatePaymentResponse(
                partnerCode,
                requestId,
                orderId,
                amount,
                responseTime,
                resultCode == 0 ? "Successful." : "Transaction denied",
                resultCode,
                payUrl,
                null,
                null,
                hmacSha256("momo-secret", rawSignature)
        );
    }

    private MoMoCreatePaymentResponse responseWith(
            MoMoCreatePaymentResponse response,
            Long amount,
            Integer resultCode,
            String payUrl,
            String signature
    ) {
        return new MoMoCreatePaymentResponse(
                response.partnerCode(),
                response.requestId(),
                response.orderId(),
                amount,
                response.responseTime(),
                response.message(),
                resultCode,
                payUrl,
                response.deeplink(),
                response.qrCodeUrl(),
                signature
        );
    }

    private String expectedRequestSignature(MoMoCreatePaymentRequest request, String secretKey) {
        String rawSignature = "accessKey=momo-access"
                + "&amount=" + request.amount()
                + "&extraData=" + request.extraData()
                + "&ipnUrl=" + request.ipnUrl()
                + "&orderId=" + request.orderId()
                + "&orderInfo=" + request.orderInfo()
                + "&partnerCode=" + request.partnerCode()
                + "&redirectUrl=" + request.redirectUrl()
                + "&requestId=" + request.requestId()
                + "&requestType=" + request.requestType();
        return hmacSha256(secretKey, rawSignature);
    }

    private void assertProviderError(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.PAYMENT_PROVIDER_ERROR)
                );
    }

    private void assertValidationError(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR)
                );
    }

    private PaymentWebhookRequest webhookRequest(Map<String, Object> payload) {
        return new PaymentWebhookRequest(
                "momo",
                Map.of("content-type", "application/json"),
                payload,
                Instant.parse("2026-06-13T06:00:00Z")
        );
    }

    private Map<String, Object> ipnPayload(int resultCode, long transId) {
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
        payload.put("responseTime", 1781300000000L);
        payload.put("extraData", "");
        payload.put("signature", notificationSignature(payload));
        return payload;
    }

    private String notificationSignature(Map<String, Object> payload) {
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

    private Object mismatchedValue(String fieldName) {
        return switch (fieldName) {
            case "partnerCode" -> "OTHER";
            case "orderId" -> "GORIDE-PAY-070";
            case "requestId" -> "OTHER";
            case "amount" -> 21000L;
            case "orderInfo" -> "Other order";
            case "extraData" -> "unexpected";
            default -> throw new IllegalArgumentException("Unexpected field: " + fieldName);
        };
    }

    private String hmacSha256(String secretKey, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
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

    private Payment payment(BigDecimal amount) {
        Trip trip = Trip.create(
                user(10L, UserRole.PASSENGER),
                VehicleType.MOTORBIKE,
                PaymentMethod.MOMO,
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
        trip.complete(BigDecimal.valueOf(4.2), BigDecimal.ONE, 18);

        Payment payment = Payment.createPending(trip);
        ReflectionTestUtils.setField(payment, "id", 70L);
        ReflectionTestUtils.setField(payment, "amount", amount);
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
}
