package com.example.goride.payment.provider;

import java.time.Instant;

public record PaymentCheckoutSession(
        boolean checkoutRequired,
        String checkoutUrl,
        Instant expiresAt
) {
    public PaymentCheckoutSession {
        if (checkoutRequired && (checkoutUrl == null || checkoutUrl.isBlank())) {
            throw new IllegalArgumentException("checkoutUrl is required when checkout is required");
        }
        checkoutUrl = checkoutUrl == null ? null : checkoutUrl.strip();
    }

    public static PaymentCheckoutSession notRequired() {
        return new PaymentCheckoutSession(false, null, null);
    }
}
