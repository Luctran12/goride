package com.example.goride.payment.provider;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.booking.domain.Trip;
import com.example.goride.payment.domain.Payment;

public interface PaymentProvider {
    PaymentMethod paymentMethod();

    Payment createPendingPayment(Trip trip);
}
