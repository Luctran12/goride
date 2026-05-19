package com.example.goride.matching.service;

import com.example.goride.matching.domain.DriverCandidate;
import com.example.goride.matching.domain.DriverOffer;
import com.example.goride.matching.domain.MatchingRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

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
        List<DriverCandidate> rankedCandidates = matchingStrategy.rank(
                request,
                candidateStore.findAvailableCandidates(request)
        );

        for (DriverCandidate candidate : rankedCandidates) {
            Instant offerExpiresAt = Instant.now().plus(DRIVER_LOCK_TTL);
            if (candidateStore.tryLockCandidate(request.tripId(), candidate.driverId(), DRIVER_LOCK_TTL)) {
                int attempt = 1;
                candidateStore.recordTripMatching(
                        request.tripId(),
                        candidate.driverId(),
                        attempt,
                        offerExpiresAt,
                        TRIP_MATCHING_TTL
                );
                return Optional.of(new DriverOffer(request.tripId(), candidate, attempt, offerExpiresAt));
            }
        }

        return Optional.empty();
    }
}
