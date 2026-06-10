package com.example.goride.payment.service;

import com.example.goride.booking.domain.Trip;
import com.example.goride.payment.domain.Payment;
import com.example.goride.payment.provider.PaymentProviderRegistry;
import com.example.goride.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripPaymentService {
    private final PaymentRepository paymentRepository;
    private final PaymentProviderRegistry paymentProviderRegistry;

    public TripPaymentService(PaymentRepository paymentRepository, PaymentProviderRegistry paymentProviderRegistry) {
        this.paymentRepository = paymentRepository;
        this.paymentProviderRegistry = paymentProviderRegistry;
    }

    @Transactional
    public Payment createPendingPayment(Trip trip) {
        return paymentRepository.findByTripId(trip.getId())
                .orElseGet(() -> paymentRepository.save(paymentProviderRegistry
                        .requireProvider(trip.getPaymentMethod())
                        .createPendingPayment(trip)));
    }
}
