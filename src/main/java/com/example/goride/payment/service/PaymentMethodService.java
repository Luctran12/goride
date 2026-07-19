package com.example.goride.payment.service;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.payment.config.PaymentProviderProperties;
import com.example.goride.payment.domain.PaymentSandboxUatResult;
import com.example.goride.payment.dto.PaymentMethodResponse;
import com.example.goride.payment.provider.PaymentProviderRegistry;
import com.example.goride.payment.repository.PaymentSandboxUatResultRepository;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class PaymentMethodService {
    private final PaymentProviderRegistry paymentProviderRegistry;
    private final PaymentProviderProperties paymentProviderProperties;
    private final PaymentSandboxUatResultRepository sandboxUatResultRepository;

    public PaymentMethodService(
            PaymentProviderRegistry paymentProviderRegistry,
            PaymentProviderProperties paymentProviderProperties,
            PaymentSandboxUatResultRepository sandboxUatResultRepository
    ) {
        this.paymentProviderRegistry = paymentProviderRegistry;
        this.paymentProviderProperties = paymentProviderProperties;
        this.sandboxUatResultRepository = sandboxUatResultRepository;
    }

    public List<PaymentMethodResponse> listPaymentMethods() {
        return Arrays.stream(PaymentMethod.values())
                .map(this::paymentMethodResponse)
                .toList();
    }

    public boolean isPaymentMethodEnabled(PaymentMethod paymentMethod) {
        return paymentMethodResponse(paymentMethod).enabled();
    }

    private PaymentMethodResponse paymentMethodResponse(PaymentMethod paymentMethod) {
        boolean providerRegistered = paymentProviderRegistry.supports(paymentMethod);
        if (!paymentMethod.checkoutRequired()) {
            return PaymentMethodResponse.of(paymentMethod, providerRegistered, false, true, providerRegistered, true);
        }

        PaymentProviderProperties.ProviderSettings settings =
                paymentProviderProperties.settingsFor(paymentMethod.providerName());
        boolean checkoutConfigured = checkoutConfigured(paymentMethod, settings);
        boolean webhookConfigured = webhookConfigured(paymentMethod, settings);
        boolean providerConfigured = settings.isEnabled() && checkoutConfigured;
        boolean enabled = providerRegistered && providerConfigured;
        boolean webhookReady = providerRegistered && settings.isEnabled() && webhookConfigured;
        boolean readyForFrontendExposure = enabled && webhookReady && sandboxUatResultRepository
                .findByProviderName(paymentMethod.providerName())
                .map(PaymentSandboxUatResult::passedAllRequiredChecks)
                .orElse(false);
        return PaymentMethodResponse.of(
                paymentMethod,
                enabled,
                settings.isSandbox(),
                providerConfigured,
                providerRegistered,
                readyForFrontendExposure
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
}
