package com.example.goride.matching.service;

import com.example.goride.matching.domain.DriverCandidate;
import com.example.goride.matching.domain.DriverOffer;
import com.example.goride.matching.domain.MatchingRequest;
import com.example.goride.matching.telemetry.MatchingTelemetryFailureReporter;
import com.example.goride.matching.telemetry.MatchingTelemetryPort;
import com.example.goride.matching.telemetry.MatchingTelemetryTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

@Service
public class MatchingService {
    private static final Logger log = LoggerFactory.getLogger(MatchingService.class);
    private static final Duration DRIVER_LOCK_TTL = Duration.ofSeconds(30);
    private static final Duration MATCHING_SEARCH_LOCK_TTL = Duration.ofSeconds(30);
    private static final Duration TRIP_MATCHING_TTL = Duration.ofMinutes(5);

    private final DriverCandidateStore candidateStore;
    private final DriverMatchingStrategy matchingStrategy;
    private final MatchingTelemetryPort matchingTelemetry;
    private final MatchingTelemetryFailureReporter failureReporter;
    private final Clock clock;

    public MatchingService(
            DriverCandidateStore candidateStore,
            DriverMatchingStrategy matchingStrategy,
            MatchingTelemetryPort matchingTelemetry,
            MatchingTelemetryFailureReporter failureReporter,
            Clock clock
    ) {
        this.candidateStore = candidateStore;
        this.matchingStrategy = matchingStrategy;
        this.matchingTelemetry = matchingTelemetry;
        this.failureReporter = failureReporter;
        this.clock = clock;
    }

    public Optional<DriverOffer> findAndLockDriver(MatchingRequest request) {
        return findAndLockDriver(request, MatchingTelemetryTrigger.BOOKING_CREATED);
    }

    public Optional<DriverOffer> findAndLockDriver(
            MatchingRequest request,
            MatchingTelemetryTrigger trigger
    ) {
        return findAndLockDriver(request, null, Set.of(), trigger);
    }

    public Optional<DriverOffer> findAndLockDriver(
            MatchingRequest request,
            int attempt,
            Set<Long> excludedDriverIds
    ) {
        return findAndLockDriver(
                request,
                attempt,
                excludedDriverIds,
                MatchingTelemetryTrigger.RECOVERY
        );
    }

    private Optional<DriverOffer> findAndLockDriver(
            MatchingRequest request,
            Integer requestedAttempt,
            Set<Long> excludedDriverIds,
            MatchingTelemetryTrigger trigger
    ) {
        if (requestedAttempt != null && requestedAttempt <= 0) {
            throw new IllegalArgumentException("attempt must be positive");
        }
        Set<Long> excludedDrivers = excludedDriverIds == null ? Set.of() : Set.copyOf(excludedDriverIds);
        Optional<String> searchLockToken = candidateStore.tryLockMatchingSearch(
                request.tripId(),
                MATCHING_SEARCH_LOCK_TTL
        );
        if (searchLockToken.isEmpty()) {
            log.info("Concurrent matching search skipped tripId={}", request.tripId());
            return Optional.empty();
        }
        try {
            return findAndLockDriverUnderSearchLock(
                    request,
                    requestedAttempt,
                    excludedDrivers,
                    trigger
            );
        } finally {
            try {
                candidateStore.releaseMatchingSearchLock(request.tripId(), searchLockToken.get());
            } catch (RuntimeException exception) {
                failureReporter.report("release_search_lock", request.tripId(), exception);
            }
        }
    }

    private Optional<DriverOffer> findAndLockDriverUnderSearchLock(
            MatchingRequest request,
            Integer requestedAttempt,
            Set<Long> excludedDrivers,
            MatchingTelemetryTrigger trigger
    ) {
        List<DriverCandidate> rankedCandidates = matchingStrategy.rank(
                request,
                candidateStore.findAvailableCandidates(request)
        ).stream()
                .filter(candidate -> !excludedDrivers.contains(candidate.driverId()))
                .toList();

        OptionalInt telemetryAttempt;
        try {
            telemetryAttempt = matchingTelemetry.beginSearch(
                    request.tripId(),
                    trigger,
                    requestedAttempt,
                    rankedCandidates.size(),
                    clock.instant()
            );
        } catch (RuntimeException exception) {
            failureReporter.report("begin_search", request.tripId(), exception);
            throw exception;
        }
        if (telemetryAttempt.isEmpty()) {
            return Optional.empty();
        }

        int attempt = telemetryAttempt.getAsInt();
        for (int index = 0; index < rankedCandidates.size(); index++) {
            DriverCandidate candidate = rankedCandidates.get(index);
            Instant offeredAt = clock.instant();
            Instant offerExpiresAt = offeredAt.plus(DRIVER_LOCK_TTL);
            if (candidateStore.tryLockCandidate(request.tripId(), candidate.driverId(), DRIVER_LOCK_TTL)) {
                try {
                    boolean created = matchingTelemetry.recordOffer(
                            request.tripId(),
                            candidate.driverId(),
                            attempt,
                            index + 1,
                            candidate.distanceMeters(),
                            offeredAt,
                            offerExpiresAt
                    );
                    if (!created) {
                        candidateStore.releaseCandidateLock(candidate.driverId());
                        return Optional.empty();
                    }
                } catch (RuntimeException exception) {
                    candidateStore.releaseCandidateLock(candidate.driverId());
                    failureReporter.report("record_offer", request.tripId(), exception);
                    throw exception;
                }
                try {
                    recordMatchingState(request, attempt, excludedDrivers, candidate, offerExpiresAt);
                } catch (RuntimeException exception) {
                    compensateOperationalState(request.tripId(), candidate.driverId());
                    failureReporter.report("record_operational_state", request.tripId(), exception);
                    throw exception;
                }
                return Optional.of(new DriverOffer(request.tripId(), candidate, attempt, offerExpiresAt));
            }
        }

        log.info(
                "Matching search produced no lockable candidate tripId={} attempt={} candidateCount={}",
                request.tripId(),
                attempt,
                rankedCandidates.size()
        );
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

    private void compensateOperationalState(Long tripId, Long driverId) {
        candidateStore.releaseCandidateLock(driverId);
        candidateStore.clearTripMatching(tripId);
    }

}
