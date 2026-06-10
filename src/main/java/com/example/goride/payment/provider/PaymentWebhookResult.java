package com.example.goride.payment.provider;

import com.example.goride.payment.domain.PaymentStatus;

public record PaymentWebhookResult(
        boolean accepted,
        Long paymentId,
        Long tripId,
        PaymentStatus status,
        String transactionRef,
        String message
) {
    public PaymentWebhookResult {
        if (message != null) {
            message = message.strip();
        }
        if (transactionRef != null) {
            transactionRef = transactionRef.strip();
        }
    }
}
