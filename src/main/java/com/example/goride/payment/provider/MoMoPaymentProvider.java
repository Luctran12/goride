package com.example.goride.payment.provider;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.booking.domain.Trip;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.payment.config.PaymentProviderProperties;
import com.example.goride.payment.domain.Payment;
import com.example.goride.payment.domain.PaymentStatus;
import com.example.goride.payment.repository.PaymentRepository;
import com.example.goride.payment.service.PaymentCompletionWorkflow;
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
    private static final int SUCCESS_RESULT_CODE = 0;
    private static final int AUTHORIZED_RESULT_CODE = 9000;

    private final PaymentProviderProperties paymentProviderProperties;
    private final MoMoPaymentClient paymentClient;
    private final PaymentRepository paymentRepository;
    private final PaymentCompletionWorkflow paymentCompletionWorkflow;

    public MoMoPaymentProvider(
            PaymentProviderProperties paymentProviderProperties,
            MoMoPaymentClient paymentClient,
            PaymentRepository paymentRepository,
            PaymentCompletionWorkflow paymentCompletionWorkflow
    ) {
        this.paymentProviderProperties = paymentProviderProperties;
        this.paymentClient = paymentClient;
        this.paymentRepository = paymentRepository;
        this.paymentCompletionWorkflow = paymentCompletionWorkflow;
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

    @Override
    public PaymentWebhookResult handleWebhook(PaymentWebhookRequest request) {
        PaymentProviderProperties.ProviderSettings settings =
                paymentProviderProperties.settingsFor(providerName());
        if (!settings.isEnabled() || !settings.hasMomoWebhookConfiguration()) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_PROVIDER_UNSUPPORTED,
                    "MoMo webhook is not configured"
            );
        }

        MoMoPaymentNotification notification = paymentNotification(request);
        verifyNotificationSignature(settings, notification);
        Long paymentId = paymentId(notification.orderId());
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        validateNotification(settings, notification, payment);

        PaymentStatus previousStatus = payment.getStatus();
        String transactionRef = Long.toString(notification.transId());
        boolean successful = isSuccessfulResultCode(notification.resultCode());
        if (successful) {
            payment.markCompletedByProvider(providerName(), transactionRef);
        } else {
            payment.markFailedByProvider(providerName(), transactionRef);
        }
        Payment savedPayment = paymentRepository.save(payment);
        if (successful && previousStatus != PaymentStatus.COMPLETED) {
            paymentCompletionWorkflow.handleCompletedPayment(savedPayment);
        }
        return new PaymentWebhookResult(
                true,
                savedPayment.getId(),
                savedPayment.getTrip().getId(),
                savedPayment.getStatus(),
                savedPayment.getTransactionRef(),
                successful
                        ? "MoMo payment completed"
                        : "MoMo payment failed"
        );
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

    private MoMoPaymentNotification paymentNotification(PaymentWebhookRequest request) {
        return new MoMoPaymentNotification(
                requiredString(request, "partnerCode"),
                requiredString(request, "orderId"),
                requiredString(request, "requestId"),
                requiredLong(request, "amount"),
                requiredString(request, "orderInfo"),
                requiredString(request, "orderType"),
                requiredLong(request, "transId"),
                requiredInt(request, "resultCode"),
                stringValue(request, "message", true),
                requiredString(request, "payType"),
                requiredLong(request, "responseTime"),
                stringValue(request, "extraData", true),
                requiredString(request, "signature")
        );
    }

    private void verifyNotificationSignature(
            PaymentProviderProperties.ProviderSettings settings,
            MoMoPaymentNotification notification
    ) {
        String rawSignature = "accessKey=" + settings.normalizedAccessKey()
                + "&amount=" + notification.amount()
                + "&extraData=" + notification.extraData()
                + "&message=" + notification.message()
                + "&orderId=" + notification.orderId()
                + "&orderInfo=" + notification.orderInfo()
                + "&orderType=" + notification.orderType()
                + "&partnerCode=" + notification.partnerCode()
                + "&payType=" + notification.payType()
                + "&requestId=" + notification.requestId()
                + "&responseTime=" + notification.responseTime()
                + "&resultCode=" + notification.resultCode()
                + "&transId=" + notification.transId();
        String actualSignature = hmacSha256(settings.normalizedSecretKey(), rawSignature);
        if (!MessageDigest.isEqual(
                actualSignature.getBytes(StandardCharsets.UTF_8),
                notification.signature().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)
        )) {
            throw validationError("Invalid MoMo IPN signature");
        }
    }

    private void validateNotification(
            PaymentProviderProperties.ProviderSettings settings,
            MoMoPaymentNotification notification,
            Payment payment
    ) {
        if (payment.getMethod() != PaymentMethod.MOMO) {
            throw validationError("Payment method is not MoMo");
        }
        long expectedAmount = momoAmount(payment.getAmount());
        String expectedOrderId = orderId(payment);
        String expectedRequestId = requestId(payment);
        String expectedOrderInfo = orderInfo(payment);
        if (!settings.normalizedMerchantId().equals(notification.partnerCode())
                || !expectedOrderId.equals(notification.orderId())
                || !expectedRequestId.equals(notification.requestId())
                || expectedAmount != notification.amount()
                || !expectedOrderInfo.equals(notification.orderInfo())
                || !EXTRA_DATA.equals(notification.extraData())) {
            throw validationError("MoMo IPN data does not match the payment");
        }
        if (notification.transId() <= 0 || notification.responseTime() <= 0) {
            throw validationError("MoMo IPN transaction metadata is invalid");
        }
    }

    private Long paymentId(String orderId) {
        if (!orderId.startsWith(ORDER_ID_PREFIX)) {
            throw validationError("MoMo orderId is invalid");
        }
        try {
            return Long.parseLong(orderId.substring(ORDER_ID_PREFIX.length()));
        } catch (NumberFormatException exception) {
            throw validationError("MoMo orderId is invalid");
        }
    }

    private boolean isSuccessfulResultCode(int resultCode) {
        return resultCode == SUCCESS_RESULT_CODE || resultCode == AUTHORIZED_RESULT_CODE;
    }

    private String requiredString(PaymentWebhookRequest request, String fieldName) {
        return stringValue(request, fieldName, false);
    }

    private String stringValue(
            PaymentWebhookRequest request,
            String fieldName,
            boolean allowEmpty
    ) {
        Object value = request.payload().get(fieldName);
        if (!(value instanceof String stringValue)
                || (!allowEmpty && stringValue.isBlank())) {
            throw validationError("MoMo IPN field is invalid: " + fieldName);
        }
        return stringValue;
    }

    private long requiredLong(PaymentWebhookRequest request, String fieldName) {
        Object value = request.payload().get(fieldName);
        try {
            if (value instanceof Number number) {
                return new BigDecimal(number.toString()).longValueExact();
            }
            if (value instanceof String stringValue && !stringValue.isBlank()) {
                return new BigDecimal(stringValue).longValueExact();
            }
        } catch (ArithmeticException | NumberFormatException exception) {
            throw validationError("MoMo IPN field is invalid: " + fieldName);
        }
        throw validationError("MoMo IPN field is invalid: " + fieldName);
    }

    private int requiredInt(PaymentWebhookRequest request, String fieldName) {
        long value = requiredLong(request, fieldName);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw validationError("MoMo IPN field is invalid: " + fieldName);
        }
        return (int) value;
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

    private BusinessException validationError(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }

    private record MoMoPaymentNotification(
            String partnerCode,
            String orderId,
            String requestId,
            long amount,
            String orderInfo,
            String orderType,
            long transId,
            int resultCode,
            String message,
            String payType,
            long responseTime,
            String extraData,
            String signature
    ) {
    }
}
