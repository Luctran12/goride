package com.example.goride.payment.service;

import com.example.goride.booking.domain.Trip;
import com.example.goride.payment.domain.Payment;
import com.example.goride.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripPaymentService {
    private final PaymentRepository paymentRepository;

    public TripPaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public Payment createPendingPayment(Trip trip) {
        return paymentRepository.findByTripId(trip.getId())
                .orElseGet(() -> paymentRepository.save(Payment.createPending(trip)));
    }
}
