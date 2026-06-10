package com.example.goride.payment.dto;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.payment.domain.Payment;
import com.example.goride.payment.domain.PaymentStatus;
import com.example.goride.payment.provider.PaymentCheckoutSession;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentCheckoutResponse(
        Long paymentId,
        Long tripId,
        BigDecimal amount,
        PaymentMethod method,
        PaymentStatus status,
        String provider,
        boolean checkoutRequired,
        String checkoutUrl,
        Instant expiresAt
) {
    public static PaymentCheckoutResponse from(Payment payment, PaymentCheckoutSession checkoutSession) {
        return new PaymentCheckoutResponse(
                payment.getId(),
                payment.getTrip().getId(),
                payment.getAmount(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getProvider(),
                checkoutSession.checkoutRequired(),
                checkoutSession.checkoutUrl(),
                checkoutSession.expiresAt()
        );
    }
}
