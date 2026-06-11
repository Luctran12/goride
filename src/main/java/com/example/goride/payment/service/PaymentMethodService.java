package com.example.goride.payment.service;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.payment.config.PaymentProviderProperties;
import com.example.goride.payment.dto.PaymentMethodResponse;
import com.example.goride.payment.provider.PaymentProviderRegistry;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class PaymentMethodService {
    private final PaymentProviderRegistry paymentProviderRegistry;
    private final PaymentProviderProperties paymentProviderProperties;

    public PaymentMethodService(
            PaymentProviderRegistry paymentProviderRegistry,
            PaymentProviderProperties paymentProviderProperties
    ) {
        this.paymentProviderRegistry = paymentProviderRegistry;
        this.paymentProviderProperties = paymentProviderProperties;
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
            return PaymentMethodResponse.of(paymentMethod, providerRegistered, false, true, providerRegistered);
        }

        PaymentProviderProperties.ProviderSettings settings =
                paymentProviderProperties.settingsFor(paymentMethod.providerName());
        boolean providerConfigured = settings.isEnabled() && settings.hasCheckoutConfiguration();
        boolean enabled = providerRegistered && providerConfigured;
        return PaymentMethodResponse.of(
                paymentMethod,
                enabled,
                settings.isSandbox(),
                providerConfigured,
                providerRegistered
        );
    }
}
