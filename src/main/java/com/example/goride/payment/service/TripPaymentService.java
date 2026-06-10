package com.example.goride.payment.service;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.booking.domain.Trip;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.payment.domain.Payment;
import com.example.goride.payment.provider.PaymentProvider;
import com.example.goride.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class TripPaymentService {
    private final PaymentRepository paymentRepository;
    private final Map<PaymentMethod, PaymentProvider> paymentProviders;

    public TripPaymentService(PaymentRepository paymentRepository, List<PaymentProvider> paymentProviders) {
        this.paymentRepository = paymentRepository;
        this.paymentProviders = providersByMethod(paymentProviders);
    }

    @Transactional
    public Payment createPendingPayment(Trip trip) {
        return paymentRepository.findByTripId(trip.getId())
                .orElseGet(() -> paymentRepository.save(paymentProviderFor(trip.getPaymentMethod()).createPendingPayment(trip)));
    }

    private PaymentProvider paymentProviderFor(PaymentMethod paymentMethod) {
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
