package com.example.goride.payment.dto;

import com.example.goride.payment.domain.PaymentStatus;
import com.example.goride.payment.provider.PaymentWebhookResult;

public record PaymentWebhookResponse(
        String provider,
        boolean accepted,
        Long paymentId,
        Long tripId,
        PaymentStatus status,
        String transactionRef,
        String message
) {
    public static PaymentWebhookResponse from(String provider, PaymentWebhookResult result) {
        return new PaymentWebhookResponse(
                provider,
                result.accepted(),
                result.paymentId(),
                result.tripId(),
                result.status(),
                result.transactionRef(),
                result.message()
        );
    }
}
