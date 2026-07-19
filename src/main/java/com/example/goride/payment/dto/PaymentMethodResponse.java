package com.example.goride.payment.dto;

import com.example.goride.booking.domain.PaymentMethod;

public record PaymentMethodResponse(
        PaymentMethod method,
        String provider,
        String displayName,
        boolean enabled,
        boolean checkoutRequired,
        boolean sandbox,
        boolean providerConfigured,
        boolean providerRegistered,
        boolean readyForFrontendExposure,
        boolean consumerEnabled
) {
    public static PaymentMethodResponse of(
            PaymentMethod method,
            boolean enabled,
            boolean sandbox,
            boolean providerConfigured,
            boolean providerRegistered,
            boolean readyForFrontendExposure
    ) {
        return new PaymentMethodResponse(
                method,
                method.providerName(),
                method.displayName(),
                enabled,
                method.checkoutRequired(),
                sandbox,
                providerConfigured,
                providerRegistered,
                readyForFrontendExposure,
                enabled && readyForFrontendExposure
        );
    }
}
