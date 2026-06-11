package com.example.goride.payment.provider;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.booking.domain.Trip;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.payment.config.PaymentProviderProperties;
import com.example.goride.payment.domain.Payment;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;

@Component
public class VnPayPaymentProvider implements PaymentProvider {
    private static final DateTimeFormatter VNPAY_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final int CHECKOUT_EXPIRY_MINUTES = 15;

    private final PaymentProviderProperties paymentProviderProperties;
    private final Clock clock;

    public VnPayPaymentProvider(PaymentProviderProperties paymentProviderProperties, Clock clock) {
        this.paymentProviderProperties = paymentProviderProperties;
        this.clock = clock;
    }

    @Override
    public PaymentMethod paymentMethod() {
        return PaymentMethod.VNPAY;
    }

    @Override
    public Payment createPendingPayment(Trip trip) {
        return Payment.createPending(trip);
    }

    @Override
    public PaymentCheckoutSession createCheckoutSession(Payment payment) {
        PaymentProviderProperties.ProviderSettings settings =
                paymentProviderProperties.settingsFor(providerName());
        if (!settings.isEnabled() || !settings.hasCheckoutConfiguration()) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_PROVIDER_UNSUPPORTED,
                    "VNPay checkout is not configured"
            );
        }

        Instant now = clock.instant();
        Instant expiresAt = now.plusSeconds(CHECKOUT_EXPIRY_MINUTES * 60L);
        Map<String, String> params = checkoutParameters(payment, settings, now, expiresAt);
        String signedQuery = signedQuery(params, settings.normalizedSecretKey());
        return new PaymentCheckoutSession(
                true,
                settings.normalizedCheckoutBaseUrl() + querySeparator(settings.normalizedCheckoutBaseUrl()) + signedQuery,
                expiresAt
        );
    }

    private String querySeparator(String checkoutBaseUrl) {
        return checkoutBaseUrl.contains("?") ? "&" : "?";
    }

    private Map<String, String> checkoutParameters(
            Payment payment,
            PaymentProviderProperties.ProviderSettings settings,
            Instant createdAt,
            Instant expiresAt
    ) {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", settings.normalizedMerchantId());
        params.put("vnp_Amount", vnpayAmount(payment.getAmount()));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", transactionReference(payment));
        params.put("vnp_OrderInfo", "GoRide trip " + payment.getTrip().getId() + " payment " + payment.getId());
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", settings.normalizedReturnUrl());
        params.put("vnp_IpAddr", settings.normalizedDefaultIpAddress());
        params.put("vnp_CreateDate", VNPAY_TIME_FORMAT.format(createdAt));
        params.put("vnp_ExpireDate", VNPAY_TIME_FORMAT.format(expiresAt));
        return params;
    }

    private String transactionReference(Payment payment) {
        if (payment.getId() == null) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_NOT_FOUND,
                    "Payment id is required before creating VNPay checkout"
            );
        }
        return "GORIDE-PAY-" + payment.getId();
    }

    private String vnpayAmount(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.UNNECESSARY)
                .toPlainString();
    }

    private String signedQuery(Map<String, String> params, String secretKey) {
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            appendParam(hashData, entry.getKey(), entry.getValue());
            appendParam(query, entry.getKey(), entry.getValue());
        }
        String secureHash = hmacSha512(secretKey, trimTrailingAmpersand(hashData).toString());
        return trimTrailingAmpersand(query).append("&vnp_SecureHash=").append(secureHash).toString();
    }

    private void appendParam(StringBuilder builder, String key, String value) {
        if (!builder.isEmpty()) {
            builder.append('&');
        }
        builder.append(urlEncode(key)).append('=').append(urlEncode(value));
    }

    private StringBuilder trimTrailingAmpersand(StringBuilder builder) {
        if (!builder.isEmpty() && builder.charAt(builder.length() - 1) == '&') {
            builder.deleteCharAt(builder.length() - 1);
        }
        return builder;
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
            throw new IllegalStateException("Unable to sign VNPay checkout request", exception);
        }
    }
}
