package com.example.goride.payment.service;

import com.example.goride.booking.domain.Trip;
import com.example.goride.payment.domain.Payment;
import com.example.goride.payment.domain.PaymentStatus;
import com.example.goride.payment.provider.PaymentProviderRegistry;
import com.example.goride.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripPaymentService {
    private final PaymentRepository paymentRepository;
    private final PaymentProviderRegistry paymentProviderRegistry;
    private final PaymentCompletionWorkflow paymentCompletionWorkflow;

    public TripPaymentService(
            PaymentRepository paymentRepository,
            PaymentProviderRegistry paymentProviderRegistry,
            PaymentCompletionWorkflow paymentCompletionWorkflow
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentProviderRegistry = paymentProviderRegistry;
        this.paymentCompletionWorkflow = paymentCompletionWorkflow;
    }

    @Transactional
    public Payment createPendingPayment(Trip trip) {
        Payment payment = paymentRepository.findByTripId(trip.getId())
                .orElseGet(() -> paymentRepository.save(paymentProviderRegistry
                        .requireProvider(trip.getPaymentMethod())
                        .createPendingPayment(trip)));
        if (payment.getStatus() == PaymentStatus.PENDING && payment.getMethod().checkoutRequired()) {
            paymentCompletionWorkflow.handleOnlinePaymentAwaitingCheckout(payment);
        }
        return payment;
    }
}
