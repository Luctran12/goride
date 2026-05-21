package com.example.goride.matching.service;

import com.example.goride.matching.domain.DriverCandidate;
import com.example.goride.matching.domain.MatchingRequest;
import com.example.goride.matching.domain.TripMatchingState;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface DriverCandidateStore {
    List<DriverCandidate> findAvailableCandidates(MatchingRequest request);

    boolean tryLockCandidate(Long tripId, Long driverId, Duration lockTtl);

    void recordTripMatching(Long tripId, Long driverId, int attempt, Instant offerExpiresAt, Duration ttl);

    void recordTripMatching(
            Long tripId,
            Long driverId,
            int attempt,
            Instant offerExpiresAt,
            Collection<Long> rejectedDriverIds,
            Duration ttl
    );

    Optional<TripMatchingState> findTripMatching(Long tripId);

    Set<Long> findActiveMatchingTripIds();

    void clearTripMatching(Long tripId);

    void releaseCandidateLock(Long driverId);

    void markCandidateBusy(Long driverId);
}
