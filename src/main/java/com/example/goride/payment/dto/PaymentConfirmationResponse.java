package com.example.goride.payment.dto;

import com.example.goride.payment.domain.Payment;
import com.example.goride.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentConfirmationResponse(
        Long tripId,
        PaymentStatus status,
        BigDecimal amount,
        Instant paidAt
) {
    public static PaymentConfirmationResponse from(Payment payment) {
        return new PaymentConfirmationResponse(
                payment.getTrip().getId(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getPaidAt()
        );
    }
}
