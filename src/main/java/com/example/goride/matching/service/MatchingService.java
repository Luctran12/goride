package com.example.goride.matching.service;

import com.example.goride.matching.domain.DriverCandidate;
import com.example.goride.matching.domain.DriverOffer;
import com.example.goride.matching.domain.MatchingRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class MatchingService {
    private static final Duration DRIVER_LOCK_TTL = Duration.ofSeconds(30);
    private static final Duration TRIP_MATCHING_TTL = Duration.ofMinutes(5);

    private final DriverCandidateStore candidateStore;
    private final DriverMatchingStrategy matchingStrategy;

    public MatchingService(DriverCandidateStore candidateStore, DriverMatchingStrategy matchingStrategy) {
        this.candidateStore = candidateStore;
        this.matchingStrategy = matchingStrategy;
    }

    public Optional<DriverOffer> findAndLockDriver(MatchingRequest request) {
        return findAndLockDriver(request, 1, Set.of());
    }

    public Optional<DriverOffer> findAndLockDriver(
            MatchingRequest request,
            int attempt,
            Set<Long> excludedDriverIds
    ) {
        if (attempt <= 0) {
            throw new IllegalArgumentException("attempt must be positive");
        }
        Set<Long> excludedDrivers = excludedDriverIds == null ? Set.of() : Set.copyOf(excludedDriverIds);
        List<DriverCandidate> rankedCandidates = matchingStrategy.rank(
                request,
                candidateStore.findAvailableCandidates(request)
        ).stream()
                .filter(candidate -> !excludedDrivers.contains(candidate.driverId()))
                .toList();

        for (DriverCandidate candidate : rankedCandidates) {
            Instant offerExpiresAt = Instant.now().plus(DRIVER_LOCK_TTL);
            if (candidateStore.tryLockCandidate(request.tripId(), candidate.driverId(), DRIVER_LOCK_TTL)) {
                recordMatchingState(request, attempt, excludedDrivers, candidate, offerExpiresAt);
                return Optional.of(new DriverOffer(request.tripId(), candidate, attempt, offerExpiresAt));
            }
        }

        return Optional.empty();
    }

    private void recordMatchingState(
            MatchingRequest request,
            int attempt,
            Set<Long> excludedDrivers,
            DriverCandidate candidate,
            Instant offerExpiresAt
    ) {
        if (excludedDrivers.isEmpty()) {
            candidateStore.recordTripMatching(
                    request.tripId(),
                    candidate.driverId(),
                    attempt,
                    offerExpiresAt,
                    TRIP_MATCHING_TTL
            );
            return;
        }
        candidateStore.recordTripMatching(
                request.tripId(),
                candidate.driverId(),
                attempt,
                offerExpiresAt,
                excludedDrivers,
                TRIP_MATCHING_TTL
        );
    }
}
