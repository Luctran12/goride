package com.example.goride.matching.service;

import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.domain.TripStatus;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.matching.domain.DriverOffer;
import com.example.goride.matching.domain.MatchingRequest;
import com.example.goride.matching.domain.TripMatchingState;
import com.example.goride.matching.notification.DriverOfferNotification;
import com.example.goride.matching.notification.DriverOfferNotifier;
import com.example.goride.matching.telemetry.ExpiredMatchingOffer;
import com.example.goride.matching.telemetry.MatchingTelemetryFailureReporter;
import com.example.goride.matching.telemetry.MatchingTelemetryOfferOutcome;
import com.example.goride.matching.telemetry.MatchingTelemetryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

@Service
public class MatchingOfferTimeoutService {
    private final TripRepository tripRepository;
    private final DriverCandidateStore candidateStore;
    private final MatchingService matchingService;
    private final DriverOfferNotifier driverOfferNotifier;
    private final MatchingTelemetryPort matchingTelemetry;
    private final MatchingTelemetryFailureReporter matchingTelemetryFailureReporter;
    private final Clock clock;

    public MatchingOfferTimeoutService(
            TripRepository tripRepository,
            DriverCandidateStore candidateStore,
            MatchingService matchingService,
            DriverOfferNotifier driverOfferNotifier,
            MatchingTelemetryPort matchingTelemetry,
            MatchingTelemetryFailureReporter matchingTelemetryFailureReporter,
            Clock clock
    ) {
        this.tripRepository = tripRepository;
        this.candidateStore = candidateStore;
        this.matchingService = matchingService;
        this.driverOfferNotifier = driverOfferNotifier;
        this.matchingTelemetry = matchingTelemetry;
        this.matchingTelemetryFailureReporter = matchingTelemetryFailureReporter;
        this.clock = clock;
    }

    @Transactional
    public boolean processExpiredOffer(Long tripId) {
        Optional<TripMatchingState> matchingState = candidateStore.findTripMatching(tripId);
        if (matchingState.isEmpty()) {
            candidateStore.clearTripMatching(tripId);
            return false;
        }
        if (matchingState.get().offerExpiresAt().isAfter(clock.instant())) {
            return false;
        }
        return processExpiredOffer(matchingState.get());
    }

    @Transactional
    public boolean processRecoveredExpiredOffer(ExpiredMatchingOffer expiredOffer) {
        return processExpiredOffer(new TripMatchingState(
                expiredOffer.tripId(),
                expiredOffer.driverId(),
                expiredOffer.attempt(),
                expiredOffer.expiresAt(),
                expiredOffer.excludedDriverIds()
        ));
    }

    private boolean processExpiredOffer(TripMatchingState matchingState) {
        Long tripId = matchingState.tripId();
        Long timedOutDriverId = matchingState.offeredDriverId();

        Optional<Trip> trip = tripRepository.findActiveByIdForUpdate(tripId);
        if (trip.isEmpty() || trip.get().getStatus() != TripStatus.SEARCHING) {
            runAfterCommit(() -> clearOperationalState(tripId, timedOutDriverId));
            return true;
        }

        resolveTimedOutOffer(tripId, matchingState.attempt());
        Set<Long> rejectedDriverIds = new LinkedHashSet<>(matchingState.rejectedDriverIds());
        rejectedDriverIds.add(timedOutDriverId);
        runAfterCommit(() -> continueAfterTimeout(trip.get(), matchingState, rejectedDriverIds));
        return true;
    }

    private void continueAfterTimeout(
            Trip trip,
            TripMatchingState matchingState,
            Set<Long> rejectedDriverIds
    ) {
        clearOperationalState(matchingState.tripId(), matchingState.offeredDriverId());
        Optional<DriverOffer> nextOffer = matchingService.findAndLockDriver(
                MatchingRequest.from(trip),
                matchingState.attempt() + 1,
                rejectedDriverIds
        );
        nextOffer.ifPresent(offer -> driverOfferNotifier.notifyDriver(
                offer.candidate().driverId(),
                DriverOfferNotification.from(trip, offer)
        ));
    }

    private void clearOperationalState(Long tripId, Long driverId) {
        candidateStore.releaseCandidateLock(driverId);
        candidateStore.clearTripMatching(tripId);
    }

    private void resolveTimedOutOffer(Long tripId, int attempt) {
        try {
            matchingTelemetry.resolveOffer(
                    tripId,
                    attempt,
                    MatchingTelemetryOfferOutcome.TIMEOUT,
                    clock.instant()
            );
        } catch (RuntimeException exception) {
            matchingTelemetryFailureReporter.report("timeout_offer", tripId, exception);
            throw exception;
        }
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
