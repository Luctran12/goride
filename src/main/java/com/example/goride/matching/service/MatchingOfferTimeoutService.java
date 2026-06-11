package com.example.goride.matching.service;

import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.domain.TripStatus;
import com.example.goride.booking.domain.TripStatusHistory;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.booking.repository.TripStatusHistoryRepository;
import com.example.goride.matching.domain.DriverOffer;
import com.example.goride.matching.domain.MatchingRequest;
import com.example.goride.matching.domain.TripMatchingState;
import com.example.goride.matching.notification.DriverOfferNotification;
import com.example.goride.matching.notification.DriverOfferNotifier;
import com.example.goride.notification.dto.TripStatusNotification;
import com.example.goride.notification.dto.UserNotification;
import com.example.goride.notification.service.TripRealtimeNotifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

@Service
public class MatchingOfferTimeoutService {
    private static final int MAX_MATCHING_ATTEMPTS = 3;

    private final TripRepository tripRepository;
    private final TripStatusHistoryRepository tripStatusHistoryRepository;
    private final DriverCandidateStore candidateStore;
    private final MatchingService matchingService;
    private final DriverOfferNotifier driverOfferNotifier;
    private final TripRealtimeNotifier tripRealtimeNotifier;

    public MatchingOfferTimeoutService(
            TripRepository tripRepository,
            TripStatusHistoryRepository tripStatusHistoryRepository,
            DriverCandidateStore candidateStore,
            MatchingService matchingService,
            DriverOfferNotifier driverOfferNotifier,
            TripRealtimeNotifier tripRealtimeNotifier
    ) {
        this.tripRepository = tripRepository;
        this.tripStatusHistoryRepository = tripStatusHistoryRepository;
        this.candidateStore = candidateStore;
        this.matchingService = matchingService;
        this.driverOfferNotifier = driverOfferNotifier;
        this.tripRealtimeNotifier = tripRealtimeNotifier;
    }

    @Transactional
    public boolean processExpiredOffer(Long tripId) {
        Optional<TripMatchingState> matchingState = candidateStore.findTripMatching(tripId);
        if (matchingState.isEmpty()) {
            candidateStore.clearTripMatching(tripId);
            return false;
        }
        if (matchingState.get().offerExpiresAt().isAfter(Instant.now())) {
            return false;
        }
        return processExpiredOffer(matchingState.get());
    }

    private boolean processExpiredOffer(TripMatchingState matchingState) {
        Long tripId = matchingState.tripId();
        Long timedOutDriverId = matchingState.offeredDriverId();
        candidateStore.releaseCandidateLock(timedOutDriverId);
        candidateStore.clearTripMatching(tripId);

        Optional<Trip> trip = tripRepository.findActiveByIdForUpdate(tripId);
        if (trip.isEmpty() || trip.get().getStatus() != TripStatus.SEARCHING) {
            return true;
        }

        Set<Long> rejectedDriverIds = new LinkedHashSet<>(matchingState.rejectedDriverIds());
        rejectedDriverIds.add(timedOutDriverId);
        if (matchingState.attempt() >= MAX_MATCHING_ATTEMPTS) {
            markNoDriver(trip.get(), "Matching exhausted after offer timeout");
            return true;
        }

        Optional<DriverOffer> nextOffer = matchingService.findAndLockDriver(
                MatchingRequest.from(trip.get()),
                matchingState.attempt() + 1,
                rejectedDriverIds
        );
        nextOffer.ifPresent(offer -> driverOfferNotifier.notifyDriver(
                offer.candidate().driverId(),
                DriverOfferNotification.from(trip.get(), offer)
        ));
        if (nextOffer.isEmpty()) {
            markNoDriver(trip.get(), "No more drivers available after offer timeout");
        }
        return true;
    }

    private void markNoDriver(Trip trip, String note) {
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
