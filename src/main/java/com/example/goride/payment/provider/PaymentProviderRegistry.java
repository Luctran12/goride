package com.example.goride.payment.provider;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class PaymentProviderRegistry {
    private final Map<PaymentMethod, PaymentProvider> paymentProviders;
    private final Map<String, PaymentProvider> paymentProvidersByName;

    public PaymentProviderRegistry(List<PaymentProvider> paymentProviders) {
        this.paymentProviders = providersByMethod(paymentProviders);
        this.paymentProvidersByName = providersByName(paymentProviders);
    }

    public PaymentProvider requireProvider(PaymentMethod paymentMethod) {
        PaymentProvider provider = paymentProviders.get(paymentMethod);
        if (provider == null) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_INVALID_STATUS,
                    "Payment method is not supported: " + paymentMethod
            );
        }
        return provider;
    }

    public boolean supports(PaymentMethod paymentMethod) {
        return paymentProviders.containsKey(paymentMethod);
    }

    public PaymentProvider requireProvider(String providerName) {
        String normalizedProviderName = normalizeProviderName(providerName);
        PaymentProvider provider = paymentProvidersByName.get(normalizedProviderName);
        if (provider == null) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_PROVIDER_UNSUPPORTED,
                    "Payment provider is not supported: " + providerName
            );
        }
        return provider;
    }

    private Map<PaymentMethod, PaymentProvider> providersByMethod(List<PaymentProvider> providers) {
        Map<PaymentMethod, PaymentProvider> providersByMethod = new EnumMap<>(PaymentMethod.class);
        for (PaymentProvider provider : providers) {
            PaymentProvider previous = providersByMethod.put(provider.paymentMethod(), provider);
            if (previous != null) {
                throw new IllegalStateException("Duplicate payment provider for method " + provider.paymentMethod());
            }
        }
        return Map.copyOf(providersByMethod);
    }

    private Map<String, PaymentProvider> providersByName(List<PaymentProvider> providers) {
        Map<String, PaymentProvider> providersByName = new HashMap<>();
        for (PaymentProvider provider : providers) {
            String normalizedProviderName = normalizeProviderName(provider.providerName());
            PaymentProvider previous = providersByName.put(normalizedProviderName, provider);
            if (previous != null) {
                throw new IllegalStateException("Duplicate payment provider for name " + provider.providerName());
            }
        }
        return Map.copyOf(providersByName);
    }

    private String normalizeProviderName(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Payment provider name is required");
        }
        return providerName.strip().toLowerCase(Locale.ROOT);
    }
}
