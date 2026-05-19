package com.example.goride.matching.service;

import com.example.goride.matching.domain.DriverCandidate;
import com.example.goride.matching.domain.MatchingRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public interface DriverCandidateStore {
    List<DriverCandidate> findAvailableCandidates(MatchingRequest request);

    boolean tryLockCandidate(Long tripId, Long driverId, Duration lockTtl);

    void recordTripMatching(Long tripId, Long driverId, int attempt, Instant offerExpiresAt, Duration ttl);
}
