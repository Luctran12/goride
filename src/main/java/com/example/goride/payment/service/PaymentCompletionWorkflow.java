package com.example.goride.payment.service;

import com.example.goride.booking.domain.Trip;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.matching.service.DriverCandidateStore;
import com.example.goride.notification.dto.UserNotification;
import com.example.goride.notification.service.TripRealtimeNotifier;
import com.example.goride.payment.domain.Payment;
import com.example.goride.payment.domain.PaymentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class PaymentCompletionWorkflow {
    private final DriverCandidateStore driverCandidateStore;
    private final TripRealtimeNotifier tripRealtimeNotifier;

    public PaymentCompletionWorkflow(
            DriverCandidateStore driverCandidateStore,
            TripRealtimeNotifier tripRealtimeNotifier
    ) {
        this.driverCandidateStore = driverCandidateStore;
        this.tripRealtimeNotifier = tripRealtimeNotifier;
    }

    public void handleCompletedPayment(Payment payment) {
        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_INVALID_STATUS,
                    "Only completed payment can trigger payment completion workflow"
            );
        }
        Trip trip = payment.getTrip();
        if (trip.getDriver() == null) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_INVALID_STATUS,
                    "Completed payment must belong to an assigned trip"
            );
        }

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
