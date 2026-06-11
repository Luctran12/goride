package com.example.goride.matching.service;

import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.domain.TripStatus;
import com.example.goride.booking.domain.TripStatusHistory;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.booking.repository.TripStatusHistoryRepository;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.driver.dto.DriverTripResponse;
import com.example.goride.matching.domain.DriverOffer;
import com.example.goride.matching.domain.DriverOfferDecision;
import com.example.goride.matching.domain.MatchingRequest;
import com.example.goride.matching.domain.TripMatchingState;
import com.example.goride.matching.notification.DriverOfferNotification;
import com.example.goride.matching.notification.DriverOfferNotifier;
import com.example.goride.notification.dto.TripStatusNotification;
import com.example.goride.notification.dto.UserNotification;
import com.example.goride.notification.service.TripRealtimeNotifier;
import com.example.goride.user.domain.User;
import com.example.goride.user.domain.UserRole;
import com.example.goride.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class DriverOfferResponseService {
    private static final int MAX_MATCHING_ATTEMPTS = 3;

    private final TripRepository tripRepository;
    private final TripStatusHistoryRepository tripStatusHistoryRepository;
    private final UserRepository userRepository;
    private final DriverCandidateStore candidateStore;
    private final MatchingService matchingService;
    private final DriverOfferNotifier driverOfferNotifier;
    private final TripRealtimeNotifier tripRealtimeNotifier;

    public DriverOfferResponseService(
            TripRepository tripRepository,
            TripStatusHistoryRepository tripStatusHistoryRepository,
            UserRepository userRepository,
            DriverCandidateStore candidateStore,
            MatchingService matchingService,
            DriverOfferNotifier driverOfferNotifier,
            TripRealtimeNotifier tripRealtimeNotifier
    ) {
        this.tripRepository = tripRepository;
        this.tripStatusHistoryRepository = tripStatusHistoryRepository;
        this.userRepository = userRepository;
        this.candidateStore = candidateStore;
        this.matchingService = matchingService;
        this.driverOfferNotifier = driverOfferNotifier;
        this.tripRealtimeNotifier = tripRealtimeNotifier;
    }

    @Transactional
    public DriverTripResponse respondToOffer(Long driverId, Long tripId, DriverOfferDecision decision) {
        if (decision == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Driver offer decision is required");
        }
        TripMatchingState matchingState = requireMatchingState(tripId);
        assertOfferedDriver(driverId, matchingState);
        assertOfferActive(matchingState);

        if (decision == DriverOfferDecision.ACCEPT) {
            return acceptOffer(driverId, tripId);
        }
        return rejectOffer(driverId, tripId, matchingState);
    }

    private DriverTripResponse acceptOffer(Long driverId, Long tripId) {
        Trip trip = getTripForUpdate(tripId);
        if (trip.getStatus() != TripStatus.SEARCHING) {
            candidateStore.releaseCandidateLock(driverId);
            candidateStore.clearTripMatching(tripId);
            throw new BusinessException(ErrorCode.TRIP_STATUS_INVALID_TRANSITION);
        }

        User driver = getDriver(driverId);
        TripStatus previousStatus = trip.getStatus();
        trip.accept(driver);
        Trip savedTrip = tripRepository.save(trip);
        tripStatusHistoryRepository.save(TripStatusHistory.record(
                savedTrip,
                previousStatus,
                TripStatus.ACCEPTED,
                driver,
                "Driver accepted matching offer"
        ));
        candidateStore.markCandidateBusy(driverId);
        candidateStore.releaseCandidateLock(driverId);
        candidateStore.clearTripMatching(tripId);
        notifyPassengerTripAccepted(savedTrip);
        return response(savedTrip);
    }

    private DriverTripResponse rejectOffer(Long driverId, Long tripId, TripMatchingState matchingState) {
        Trip trip = getTripForUpdate(tripId);
        if (trip.getStatus() != TripStatus.SEARCHING) {
            candidateStore.releaseCandidateLock(driverId);
            candidateStore.clearTripMatching(tripId);
            throw new BusinessException(ErrorCode.TRIP_STATUS_INVALID_TRANSITION);
        }

        candidateStore.releaseCandidateLock(driverId);
        candidateStore.clearTripMatching(tripId);

        Set<Long> rejectedDriverIds = new LinkedHashSet<>(matchingState.rejectedDriverIds());
        rejectedDriverIds.add(driverId);
        if (matchingState.attempt() >= MAX_MATCHING_ATTEMPTS) {
            return markNoDriver(trip, "Matching exhausted after driver rejection");
        }

        Optional<DriverOffer> nextOffer = matchingService.findAndLockDriver(
                MatchingRequest.from(trip),
                matchingState.attempt() + 1,
                rejectedDriverIds
        );
        nextOffer.ifPresent(offer -> driverOfferNotifier.notifyDriver(
                offer.candidate().driverId(),
                DriverOfferNotification.from(trip, offer)
        ));
        return nextOffer
                .map(offer -> response(trip))
                .orElseGet(() -> markNoDriver(trip, "No more drivers available after rejection"));
    }

    private DriverTripResponse markNoDriver(Trip trip, String note) {
        TripStatus previousStatus = trip.getStatus();
        trip.markNoDriver();
        Trip savedTrip = tripRepository.save(trip);
        tripStatusHistoryRepository.save(TripStatusHistory.record(
                savedTrip,
                previousStatus,
                TripStatus.NO_DRIVER,
                null,
                note
        ));
        candidateStore.clearTripMatching(savedTrip.getId());
        notifyPassengerNoDriver(savedTrip);
        return response(savedTrip);
    }

    private void notifyPassengerTripAccepted(Trip trip) {
        Long tripId = trip.getId();
        Long passengerId = trip.getPassenger().getId();
        UserNotification notification = UserNotification.tripAccepted(trip);
        TripStatusNotification statusNotification = TripStatusNotification.from(trip);
        runAfterCommit(() -> {
            tripRealtimeNotifier.notifyPassenger(passengerId, notification);
            tripRealtimeNotifier.broadcastTripStatus(tripId, statusNotification);
        });
    }

    private void notifyPassengerNoDriver(Trip trip) {
        Long tripId = trip.getId();
        Long passengerId = trip.getPassenger().getId();
        UserNotification notification = UserNotification.noDriverFound(trip);
        TripStatusNotification statusNotification = TripStatusNotification.from(trip);
        runAfterCommit(() -> {
            tripRealtimeNotifier.notifyPassenger(passengerId, notification);
            tripRealtimeNotifier.broadcastTripStatus(tripId, statusNotification);
        });
    }

    private TripMatchingState requireMatchingState(Long tripId) {
        return candidateStore.findTripMatching(tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MATCHING_OFFER_NOT_FOUND));
    }

    private void assertOfferedDriver(Long driverId, TripMatchingState matchingState) {
        if (!Objects.equals(driverId, matchingState.offeredDriverId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "This matching offer belongs to another driver");
        }
    }

    private void assertOfferActive(TripMatchingState matchingState) {
        if (matchingState.offerExpiresAt().isBefore(Instant.now())) {
            candidateStore.releaseCandidateLock(matchingState.offeredDriverId());
            candidateStore.clearTripMatching(matchingState.tripId());
            throw new BusinessException(ErrorCode.MATCHING_OFFER_EXPIRED);
        }
    }

    private Trip getTripForUpdate(Long tripId) {
        return tripRepository.findActiveByIdForUpdate(tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
    }

    private User getDriver(Long driverId) {
        User driver = userRepository.findByIdAndDeletedAtIsNull(driverId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!driver.hasRole(UserRole.DRIVER)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return driver;
    }

    private DriverTripResponse response(Trip trip) {
        return new DriverTripResponse(trip.getId(), trip.getStatus());
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
