package com.example.goride.matching.service;

import com.example.goride.matching.domain.TripMatchingState;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

@Service
public class OfferedTripAccessService {
    private final DriverCandidateStore candidateStore;
    private final Clock clock;

    public OfferedTripAccessService(DriverCandidateStore candidateStore, Clock clock) {
        this.candidateStore = candidateStore;
        this.clock = clock;
    }

    public boolean hasActiveOffer(Long tripId, Long driverId) {
        if (tripId == null || driverId == null) {
            return false;
        }
        return candidateStore.findTripMatching(tripId)
                .filter(state -> Objects.equals(state.offeredDriverId(), driverId))
                .filter(this::isOfferActive)
                .isPresent();
    }

    private boolean isOfferActive(TripMatchingState state) {
        return !state.offerExpiresAt().isBefore(Instant.now(clock));
    }
}