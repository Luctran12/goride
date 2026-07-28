package com.example.goride.matching.telemetry;

import java.time.Instant;
import java.util.List;
import java.util.OptionalInt;

public interface MatchingTelemetryPort {
    OptionalInt beginSearch(
            Long tripId,
            MatchingTelemetryTrigger trigger,
            Integer requestedAttempt,
            int candidateCount,
            Instant searchedAt
    );

    boolean recordOffer(
            Long tripId,
            Long driverId,
            int attempt,
            Integer candidateRank,
            double candidateDistanceM,
            Instant offeredAt,
            Instant expiresAt
    );

    void resolveOffer(
            Long tripId,
            int attempt,
            MatchingTelemetryOfferOutcome outcome,
            Instant resolvedAt
    );

    void acceptOfferAndCompleteRun(Long tripId, int attempt, Long driverId, Instant acceptedAt);

    void expireOffer(Long tripId, int attempt, Instant lateResponseAt);

    void cancelRun(Long tripId, Instant cancelledAt);

    List<ExpiredMatchingOffer> findExpiredOffers(Instant cutoff, int limit);
}
