package com.example.goride.payment.dto;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.payment.domain.Payment;
import com.example.goride.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentDetailResponse(
        Long paymentId,
        Long tripId,
        BigDecimal amount,
        PaymentMethod method,
        PaymentStatus status,
        String provider,
        String transactionRef,
        Instant paidAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static PaymentDetailResponse from(Payment payment) {
        return new PaymentDetailResponse(
                payment.getId(),
                payment.getTrip().getId(),
                payment.getAmount(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getProvider(),
                payment.getTransactionRef(),
                payment.getPaidAt(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
