package com.example.goride.payment.service;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.domain.TripStatus;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.payment.domain.Payment;
import com.example.goride.payment.domain.PaymentStatus;
import com.example.goride.payment.dto.PaymentConfirmationResponse;
import com.example.goride.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class CashPaymentConfirmationService {
    private final PaymentRepository paymentRepository;
    private final PaymentCompletionWorkflow paymentCompletionWorkflow;

    public CashPaymentConfirmationService(
            PaymentRepository paymentRepository,
            PaymentCompletionWorkflow paymentCompletionWorkflow
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentCompletionWorkflow = paymentCompletionWorkflow;
    }

    @Transactional
    public PaymentConfirmationResponse confirmCashPayment(Long driverId, Long tripId) {
        Payment payment = paymentRepository.findByTripIdForUpdate(tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        Trip trip = payment.getTrip();
        assertAssignedDriver(driverId, trip);
        assertCashPaymentCanBeConfirmed(payment);

        payment.markCompleted();
        Payment savedPayment = paymentRepository.save(payment);
        paymentCompletionWorkflow.handleCompletedPayment(savedPayment);
        return PaymentConfirmationResponse.from(savedPayment);
    }

    private void assertAssignedDriver(Long driverId, Trip trip) {
        if (trip.getDriver() == null || !Objects.equals(trip.getDriver().getId(), driverId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only the assigned driver can confirm payment");
        }
    }

    private void assertCashPaymentCanBeConfirmed(Payment payment) {
        if (payment.getTrip().getStatus() != TripStatus.COMPLETED) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_INVALID_STATUS,
                    "Payment can only be confirmed after trip completion"
            );
        }
        if (payment.getMethod() != PaymentMethod.CASH) {
            throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS, "Only cash payment can be confirmed by driver");
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_INVALID_STATUS,
                    "Only pending payment can be confirmed"
            );
        }
    }
}
