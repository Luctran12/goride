package com.example.goride.payment.provider;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.booking.domain.PricingConfig;
import com.example.goride.booking.domain.Trip;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.driver.domain.VehicleType;
import com.example.goride.payment.config.PaymentProviderProperties;
import com.example.goride.payment.domain.Payment;
import com.example.goride.user.domain.User;
import com.example.goride.user.domain.UserRole;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VnPayPaymentProviderTests {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final Instant FIXED_NOW = Instant.parse("2026-06-11T14:00:00Z");

    @Test
    void createsSignedCheckoutUrl() {
        PaymentProviderProperties properties = properties();
        VnPayPaymentProvider provider = new VnPayPaymentProvider(
                properties,
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC)
        );
        Payment payment = payment();

        PaymentCheckoutSession session = provider.createCheckoutSession(payment);

        assertThat(session.checkoutRequired()).isTrue();
        assertThat(session.expiresAt()).isEqualTo(Instant.parse("2026-06-11T14:15:00Z"));
        assertThat(session.checkoutUrl()).startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?");

        Map<String, String> params = queryParams(session.checkoutUrl());
        assertThat(params).containsEntry("vnp_Version", "2.1.0");
        assertThat(params).containsEntry("vnp_Command", "pay");
        assertThat(params).containsEntry("vnp_TmnCode", "GORIDETMN");
        assertThat(params).containsEntry("vnp_Amount", "2000000");
        assertThat(params).containsEntry("vnp_CurrCode", "VND");
        assertThat(params).containsEntry("vnp_TxnRef", "GORIDE-PAY-70");
        assertThat(params).containsEntry("vnp_OrderInfo", "GoRide trip 99 payment 70");
        assertThat(params).containsEntry("vnp_ReturnUrl", "https://api.goride.test/payments/vnpay/return");
        assertThat(params).containsEntry("vnp_IpAddr", "203.0.113.10");
        assertThat(params).containsEntry("vnp_CreateDate", "20260611210000");
        assertThat(params).containsEntry("vnp_ExpireDate", "20260611211500");
        assertThat(params.get("vnp_SecureHash")).isEqualTo(expectedSecureHash(params, "vnp-secret"));
    }

    @Test
    void rejectsCheckoutWhenProviderIsNotConfigured() {
        VnPayPaymentProvider provider = new VnPayPaymentProvider(
                new PaymentProviderProperties(),
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> provider.createCheckoutSession(payment()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.PAYMENT_PROVIDER_UNSUPPORTED)
                );
    }

    private PaymentProviderProperties properties() {
        PaymentProviderProperties properties = new PaymentProviderProperties();
        PaymentProviderProperties.ProviderSettings vnpay = properties.getVnpay();
        vnpay.setEnabled(true);
        vnpay.setMerchantId("GORIDETMN");
        vnpay.setSecretKey("vnp-secret");
        vnpay.setCheckoutBaseUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        vnpay.setReturnUrl("https://api.goride.test/payments/vnpay/return");
        vnpay.setDefaultIpAddress("203.0.113.10");
        return properties;
    }

    private Payment payment() {
        Trip trip = Trip.create(
                user(10L, UserRole.PASSENGER),
                VehicleType.MOTORBIKE,
                PaymentMethod.VNPAY,
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
        trip.complete(BigDecimal.valueOf(20000), BigDecimal.ONE, 20);

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

    private Map<String, String> queryParams(String checkoutUrl) {
        Map<String, String> params = new TreeMap<>();
        UriComponentsBuilder.fromUriString(checkoutUrl)
                .build()
                .getQueryParams()
                .forEach((key, values) -> params.put(key, urlDecode(values.get(0))));
        return params;
    }

    private String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.US_ASCII);
    }

    private String expectedSecureHash(Map<String, String> params, String secretKey) {
        StringBuilder hashData = new StringBuilder();
        params.entrySet().stream()
                .filter(entry -> !"vnp_SecureHash".equals(entry.getKey()))
                .forEach(entry -> {
                    if (!hashData.isEmpty()) {
                        hashData.append('&');
                    }
                    hashData.append(urlEncode(entry.getKey()))
                            .append('=')
                            .append(urlEncode(entry.getValue()));
                });
        return hmacSha512(secretKey, hashData.toString());
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }

    private String hmacSha512(String secretKey, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
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
