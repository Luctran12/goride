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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

@Component
public class MoMoPaymentProvider implements PaymentProvider {
    private static final String REQUEST_TYPE = "captureWallet";
    private static final String EXTRA_DATA = "";
    private static final String LANGUAGE = "vi";
    private static final String ORDER_ID_PREFIX = "GORIDE-PAY-";
    private static final String REQUEST_ID_PREFIX = "GORIDE-CREATE-";
    private static final long MINIMUM_AMOUNT = 1_000L;
    private static final long MAXIMUM_AMOUNT = 50_000_000L;

    private final PaymentProviderProperties paymentProviderProperties;
    private final MoMoPaymentClient paymentClient;

    public MoMoPaymentProvider(
            PaymentProviderProperties paymentProviderProperties,
            MoMoPaymentClient paymentClient
    ) {
        this.paymentProviderProperties = paymentProviderProperties;
        this.paymentClient = paymentClient;
    }

    @Override
    public PaymentMethod paymentMethod() {
        return PaymentMethod.MOMO;
    }

    @Override
    public Payment createPendingPayment(Trip trip) {
        return Payment.createPending(trip);
    }

    @Override
    public PaymentCheckoutSession createCheckoutSession(Payment payment) {
        PaymentProviderProperties.ProviderSettings settings =
                paymentProviderProperties.settingsFor(providerName());
        if (!settings.isEnabled() || !settings.hasMomoCheckoutConfiguration()) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_PROVIDER_UNSUPPORTED,
                    "MoMo checkout is not configured"
            );
        }

        long amount = momoAmount(payment.getAmount());
        String orderId = orderId(payment);
        String requestId = requestId(payment);
        String orderInfo = orderInfo(payment);
        String signature = requestSignature(
                settings,
                amount,
                orderId,
                orderInfo,
                requestId
        );
        MoMoCreatePaymentRequest request = new MoMoCreatePaymentRequest(
                settings.normalizedMerchantId(),
                REQUEST_TYPE,
                settings.normalizedIpnUrl(),
                settings.normalizedReturnUrl(),
                orderId,
                amount,
                orderInfo,
                requestId,
                EXTRA_DATA,
                signature,
                LANGUAGE
        );
        MoMoCreatePaymentResponse response = paymentClient.createPayment(
                settings.normalizedCheckoutBaseUrl(),
                request
        );
        validateResponse(settings, request, response);
        return new PaymentCheckoutSession(true, response.payUrl(), null);
    }

    private long momoAmount(BigDecimal amount) {
        try {
            long normalizedAmount = amount.longValueExact();
            if (normalizedAmount < MINIMUM_AMOUNT || normalizedAmount > MAXIMUM_AMOUNT) {
                throw providerError("MoMo payment amount must be between 1000 and 50000000 VND");
            }
            return normalizedAmount;
        } catch (ArithmeticException exception) {
            throw providerError("MoMo payment amount must be a whole VND value");
        }
    }

    private String orderId(Payment payment) {
        return ORDER_ID_PREFIX + requirePaymentId(payment);
    }

    private String requestId(Payment payment) {
        return REQUEST_ID_PREFIX + requirePaymentId(payment);
    }

    private Long requirePaymentId(Payment payment) {
        if (payment.getId() == null) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_NOT_FOUND,
                    "Payment id is required before creating MoMo checkout"
            );
        }
        return payment.getId();
    }

    private String orderInfo(Payment payment) {
        return "GoRide trip " + payment.getTrip().getId() + " payment " + payment.getId();
    }

    private String requestSignature(
            PaymentProviderProperties.ProviderSettings settings,
            long amount,
            String orderId,
            String orderInfo,
            String requestId
    ) {
        String rawSignature = "accessKey=" + settings.normalizedAccessKey()
                + "&amount=" + amount
                + "&extraData=" + EXTRA_DATA
                + "&ipnUrl=" + settings.normalizedIpnUrl()
                + "&orderId=" + orderId
                + "&orderInfo=" + orderInfo
                + "&partnerCode=" + settings.normalizedMerchantId()
                + "&redirectUrl=" + settings.normalizedReturnUrl()
                + "&requestId=" + requestId
                + "&requestType=" + REQUEST_TYPE;
        return hmacSha256(settings.normalizedSecretKey(), rawSignature);
    }

    private void validateResponse(
            PaymentProviderProperties.ProviderSettings settings,
            MoMoCreatePaymentRequest request,
            MoMoCreatePaymentResponse response
    ) {
        verifyRequiredResponseFields(response);
        verifyResponseSignature(settings, response);
        if (!request.partnerCode().equals(response.partnerCode())
                || !request.orderId().equals(response.orderId())
                || !request.requestId().equals(response.requestId())
                || request.amount() != response.amount()) {
            throw providerError("MoMo checkout response does not match the request");
        }
        if (response.resultCode() != 0) {
            throw providerError(
                    "MoMo rejected checkout request with result code " + response.resultCode()
            );
        }
        if (!isValidPayUrl(response.payUrl())) {
            throw providerError("MoMo checkout response is missing a valid payUrl");
        }
    }

    private void verifyRequiredResponseFields(MoMoCreatePaymentResponse response) {
        if (isBlank(response.partnerCode())
                || isBlank(response.requestId())
                || isBlank(response.orderId())
                || response.amount() == null
                || response.responseTime() == null
                || response.resultCode() == null
                || isBlank(response.signature())) {
            throw providerError("MoMo checkout response is incomplete");
        }
    }

    private void verifyResponseSignature(
            PaymentProviderProperties.ProviderSettings settings,
            MoMoCreatePaymentResponse response
    ) {
        String rawSignature = "accessKey=" + settings.normalizedAccessKey()
                + "&amount=" + response.amount()
                + "&orderId=" + response.orderId()
                + "&partnerCode=" + response.partnerCode()
                + "&payUrl=" + nullToEmpty(response.payUrl())
                + "&requestId=" + response.requestId()
                + "&responseTime=" + response.responseTime()
                + "&resultCode=" + response.resultCode();
        String actualSignature = hmacSha256(settings.normalizedSecretKey(), rawSignature);
        if (!MessageDigest.isEqual(
                actualSignature.getBytes(StandardCharsets.UTF_8),
                response.signature().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)
        )) {
            throw providerError("Invalid MoMo checkout response signature");
        }
    }

    private boolean isValidPayUrl(String payUrl) {
        if (isBlank(payUrl)) {
            return false;
        }
        try {
            URI uri = URI.create(payUrl);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
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
            throw new IllegalStateException("Unable to sign MoMo payment request", exception);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private BusinessException providerError(String message) {
        return new BusinessException(ErrorCode.PAYMENT_PROVIDER_ERROR, message);
    }
}
