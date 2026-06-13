package com.example.goride.payment.provider;

import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.payment.config.PaymentProviderProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class PaymentWebhookFreshnessPolicy {

    public void validate(
            Instant providerTimestamp,
            PaymentWebhookRequest request,
            PaymentProviderProperties.ProviderSettings settings,
            boolean idempotentReplay
    ) {
        Instant latestAllowed = request.receivedAt()
                .plusSeconds(settings.getWebhookFutureSkewSeconds());
        if (providerTimestamp.isAfter(latestAllowed)) {
            throw validationError("Payment provider callback timestamp is in the future");
        }

        Instant earliestAllowed = request.receivedAt()
                .minusSeconds(settings.getWebhookMaxAgeSeconds());
        if (!idempotentReplay && providerTimestamp.isBefore(earliestAllowed)) {
            throw validationError("Payment provider callback is too old");
        }
    }

    private BusinessException validationError(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }
}
