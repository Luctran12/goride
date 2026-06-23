package com.example.goride.payment.service;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.payment.config.PaymentProviderProperties;
import com.example.goride.payment.dto.PaymentProviderReadinessResponse;
import com.example.goride.payment.provider.PaymentProviderRegistry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class PaymentProviderReadinessService {
    private final PaymentProviderRegistry paymentProviderRegistry;
    private final PaymentProviderProperties paymentProviderProperties;

    public PaymentProviderReadinessService(
            PaymentProviderRegistry paymentProviderRegistry,
            PaymentProviderProperties paymentProviderProperties
    ) {
        this.paymentProviderRegistry = paymentProviderRegistry;
        this.paymentProviderProperties = paymentProviderProperties;
    }

    public List<PaymentProviderReadinessResponse> listProviderReadiness() {
        return Arrays.stream(PaymentMethod.values())
                .filter(PaymentMethod::checkoutRequired)
                .map(this::providerReadiness)
                .toList();
    }

    private PaymentProviderReadinessResponse providerReadiness(PaymentMethod paymentMethod) {
        PaymentProviderProperties.ProviderSettings settings =
                paymentProviderProperties.settingsFor(paymentMethod.providerName());
        boolean providerRegistered = paymentProviderRegistry.supports(paymentMethod);
        boolean checkoutConfigured = checkoutConfigured(paymentMethod, settings);
        boolean webhookConfigured = webhookConfigured(paymentMethod, settings);
        boolean checkoutReady = providerRegistered && settings.isEnabled() && checkoutConfigured;
        boolean webhookReady = providerRegistered && settings.isEnabled() && webhookConfigured;
        List<String> missingRequirements = missingRequirements(
                paymentMethod,
                settings,
                providerRegistered,
                checkoutConfigured,
                webhookConfigured
        );

        return new PaymentProviderReadinessResponse(
                paymentMethod,
                paymentMethod.providerName(),
                paymentMethod.displayName(),
                settings.isEnabled(),
                settings.isSandbox(),
                providerRegistered,
                checkoutConfigured,
                webhookConfigured,
                checkoutReady,
                webhookReady,
                settings.isSandbox() && checkoutReady && webhookReady,
                missingRequirements,
                settings.getWebhookMaxAgeSeconds(),
                settings.getWebhookFutureSkewSeconds()
        );
    }

    private boolean checkoutConfigured(
            PaymentMethod paymentMethod,
            PaymentProviderProperties.ProviderSettings settings
    ) {
        return switch (paymentMethod) {
            case MOMO -> settings.hasMomoCheckoutConfiguration();
            case VNPAY -> settings.hasVnPayCheckoutConfiguration();
            case CASH -> true;
        };
    }

    private boolean webhookConfigured(
            PaymentMethod paymentMethod,
            PaymentProviderProperties.ProviderSettings settings
    ) {
        return switch (paymentMethod) {
            case MOMO -> settings.hasMomoWebhookConfiguration();
            case VNPAY -> settings.normalizedMerchantId() != null && settings.hasWebhookConfiguration();
            case CASH -> true;
        };
    }

    private List<String> missingRequirements(
            PaymentMethod paymentMethod,
            PaymentProviderProperties.ProviderSettings settings,
            boolean providerRegistered,
            boolean checkoutConfigured,
            boolean webhookConfigured
    ) {
        List<String> missing = new ArrayList<>();
        if (!providerRegistered) {
            missing.add("provider-registered");
        }
        if (!settings.isEnabled()) {
            missing.add("provider-enabled");
        }
        if (!settings.isSandbox()) {
            missing.add("sandbox-mode");
        }
        missingConfigurationFields(paymentMethod, settings, checkoutConfigured, webhookConfigured, missing);
        return List.copyOf(missing);
    }

    private void missingConfigurationFields(
            PaymentMethod paymentMethod,
            PaymentProviderProperties.ProviderSettings settings,
            boolean checkoutConfigured,
            boolean webhookConfigured,
            List<String> missing
    ) {
        if (checkoutConfigured && webhookConfigured) {
            return;
        }
        require(settings.normalizedMerchantId(), "merchant-id", missing);
        switch (paymentMethod) {
            case MOMO -> missingMomoFields(settings, missing);
            case VNPAY -> missingVnPayFields(settings, missing);
            case CASH -> {
            }
        }
    }

    private void missingMomoFields(
            PaymentProviderProperties.ProviderSettings settings,
            List<String> missing
    ) {
        require(settings.normalizedAccessKey(), "access-key", missing);
        require(settings.normalizedSecretKey(), "secret-key", missing);
        require(settings.normalizedCheckoutBaseUrl(), "checkout-base-url", missing);
        require(settings.normalizedReturnUrl(), "return-url", missing);
        require(settings.normalizedIpnUrl(), "ipn-url", missing);
    }

    private void missingVnPayFields(
            PaymentProviderProperties.ProviderSettings settings,
            List<String> missing
    ) {
        require(settings.normalizedSecretKey(), "secret-key", missing);
        require(settings.normalizedCheckoutBaseUrl(), "checkout-base-url", missing);
        require(settings.normalizedReturnUrl(), "return-url", missing);
        require(settings.normalizedWebhookSecret(), "webhook-secret", missing);
    }

    private void require(String value, String requirement, List<String> missing) {
        if (value == null && !missing.contains(requirement)) {
            missing.add(requirement);
        }
    }
}
