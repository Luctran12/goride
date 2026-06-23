package com.example.goride.payment.dto;

import com.example.goride.booking.domain.PaymentMethod;

import java.util.List;

public record PaymentProviderReadinessResponse(
        PaymentMethod method,
        String provider,
        String displayName,
        boolean enabled,
        boolean sandbox,
        boolean providerRegistered,
        boolean checkoutConfigured,
        boolean webhookConfigured,
        boolean checkoutReady,
        boolean webhookReady,
        boolean sandboxReady,
        List<String> missingRequirements,
        long webhookMaxAgeSeconds,
        long webhookFutureSkewSeconds
) {
}
