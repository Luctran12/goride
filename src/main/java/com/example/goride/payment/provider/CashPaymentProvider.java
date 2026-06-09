package com.example.goride.payment.provider;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.booking.domain.Trip;
import com.example.goride.payment.domain.Payment;
import org.springframework.stereotype.Component;

@Component
public class CashPaymentProvider implements PaymentProvider {
    @Override
    public PaymentMethod paymentMethod() {
        return PaymentMethod.CASH;
    }

    @Override
    public Payment createPendingPayment(Trip trip) {
        return Payment.createPending(trip);
    }
}
