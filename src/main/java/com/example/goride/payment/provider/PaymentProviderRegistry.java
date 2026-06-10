package com.example.goride.payment.provider;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class PaymentProviderRegistry {
    private final Map<PaymentMethod, PaymentProvider> paymentProviders;

    public PaymentProviderRegistry(List<PaymentProvider> paymentProviders) {
        this.paymentProviders = providersByMethod(paymentProviders);
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
}
