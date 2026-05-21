package com.example.goride.driver.service;

import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.domain.TripStatus;
import com.example.goride.booking.domain.TripStatusHistory;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.booking.repository.TripStatusHistoryRepository;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.driver.dto.DriverTripResponse;
import com.example.goride.notification.dto.TripStatusNotification;
import com.example.goride.notification.dto.UserNotification;
import com.example.goride.notification.service.TripRealtimeNotifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;

@Service
public class DriverTripStatusService {
    private final TripRepository tripRepository;
    private final TripStatusHistoryRepository tripStatusHistoryRepository;
    private final TripRealtimeNotifier tripRealtimeNotifier;

    public DriverTripStatusService(
            TripRepository tripRepository,
            TripStatusHistoryRepository tripStatusHistoryRepository,
            TripRealtimeNotifier tripRealtimeNotifier
    ) {
        this.tripRepository = tripRepository;
        this.tripStatusHistoryRepository = tripStatusHistoryRepository;
        this.tripRealtimeNotifier = tripRealtimeNotifier;
    }

    @Transactional
    public DriverTripResponse updateTripStatus(Long driverId, Long tripId, TripStatus requestedStatus) {
        if (requestedStatus == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Trip status is required");
        }

        Trip trip = tripRepository.findActiveByIdForUpdate(tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
        assertAssignedDriver(driverId, trip);

        TripStatus previousStatus = trip.getStatus();
        applyStatusTransition(trip, requestedStatus);
        Trip savedTrip = tripRepository.save(trip);
        tripStatusHistoryRepository.save(TripStatusHistory.record(
                savedTrip,
                previousStatus,
                requestedStatus,
                savedTrip.getDriver(),
                "Driver updated trip status"
        ));
        notifyTripStatusChanged(savedTrip);
        return new DriverTripResponse(savedTrip.getId(), savedTrip.getStatus());
    }

    private void assertAssignedDriver(Long driverId, Trip trip) {
        if (trip.getDriver() == null || !Objects.equals(trip.getDriver().getId(), driverId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only the assigned driver can update trip status");
        }
    }

    private void applyStatusTransition(Trip trip, TripStatus requestedStatus) {
        try {
            switch (requestedStatus) {
                case ARRIVED -> trip.markArrived();
                case IN_PROGRESS -> trip.startTrip();
                case COMPLETED -> trip.complete(
                        trip.getEstimatedFare(),
                        trip.getEstimatedDistanceKm(),
                        trip.getEstimatedDurationMin()
                );
                default -> throw new BusinessException(
                        ErrorCode.TRIP_STATUS_INVALID_TRANSITION,
                        "Driver can only update trip to ARRIVED, IN_PROGRESS, or COMPLETED"
                );
            }
        } catch (IllegalStateException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.TRIP_STATUS_INVALID_TRANSITION, exception.getMessage());
        }
    }

    private void notifyTripStatusChanged(Trip trip) {
        Long tripId = trip.getId();
        Long passengerId = trip.getPassenger().getId();
        Long driverId = trip.getDriver().getId();
        TripStatus status = trip.getStatus();
        UserNotification notification = switch (status) {
            case ARRIVED -> UserNotification.driverArrived(trip);
            case IN_PROGRESS -> UserNotification.tripStarted(trip);
            case COMPLETED -> UserNotification.tripCompleted(trip);
            default -> throw new IllegalStateException("Unsupported driver trip status " + status);
        };
        TripStatusNotification statusNotification = TripStatusNotification.from(trip);
        boolean notifyDriver = status == TripStatus.COMPLETED;
        runAfterCommit(() -> {
            tripRealtimeNotifier.notifyPassenger(passengerId, notification);
            if (notifyDriver) {
                tripRealtimeNotifier.notifyUser(driverId, notification);
            }
            tripRealtimeNotifier.broadcastTripStatus(tripId, statusNotification);
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
