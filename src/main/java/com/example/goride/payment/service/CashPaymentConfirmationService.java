package com.example.goride.payment.service;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.domain.TripStatus;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.matching.service.DriverCandidateStore;
import com.example.goride.notification.dto.UserNotification;
import com.example.goride.notification.service.TripRealtimeNotifier;
import com.example.goride.payment.domain.Payment;
import com.example.goride.payment.domain.PaymentStatus;
import com.example.goride.payment.dto.PaymentConfirmationResponse;
import com.example.goride.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;

@Service
public class CashPaymentConfirmationService {
    private final PaymentRepository paymentRepository;
    private final DriverCandidateStore driverCandidateStore;
    private final TripRealtimeNotifier tripRealtimeNotifier;

    public CashPaymentConfirmationService(
            PaymentRepository paymentRepository,
            DriverCandidateStore driverCandidateStore,
            TripRealtimeNotifier tripRealtimeNotifier
    ) {
        this.paymentRepository = paymentRepository;
        this.driverCandidateStore = driverCandidateStore;
        this.tripRealtimeNotifier = tripRealtimeNotifier;
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
        notifyPaymentCompleted(savedPayment);
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

    private void notifyPaymentCompleted(Payment payment) {
        Trip trip = payment.getTrip();
        Long passengerId = trip.getPassenger().getId();
        Long driverId = trip.getDriver().getId();
        UserNotification notification = UserNotification.paymentCompleted(trip, payment);
        runAfterCommit(() -> {
            driverCandidateStore.markCandidateAvailable(driverId);
            tripRealtimeNotifier.notifyPassenger(passengerId, notification);
            tripRealtimeNotifier.notifyUser(driverId, notification);
        });
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
