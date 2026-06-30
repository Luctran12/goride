package com.example.goride.payment.dto;

import com.example.goride.booking.domain.PaymentMethod;

import java.util.List;

public record PaymentSandboxUatPlanResponse(
        List<String> prerequisites,
        List<PaymentProviderSandboxUatResponse> providers,
        List<String> validationScenarios
) {
    public record PaymentProviderSandboxUatResponse(
            PaymentMethod method,
            String provider,
            String displayName,
            String status,
            boolean enabled,
            boolean sandbox,
            boolean checkoutReady,
            boolean webhookReady,
            boolean sandboxReady,
            String checkoutEndpoint,
            String webhookEndpoint,
            String returnUrl,
            String ipnUrl,
            List<String> missingRequirements,
            List<String> frontendActions,
            List<String> backendChecks
    ) {
    }
}
