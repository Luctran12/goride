package com.example.goride.analytics.service;

import com.example.goride.analytics.domain.MatchingOfferEvent;
import com.example.goride.analytics.domain.MatchingOfferOutcome;
import com.example.goride.analytics.domain.MatchingRun;
import com.example.goride.analytics.domain.MatchingRunOutcome;
import com.example.goride.analytics.domain.MatchingTriggerType;
import com.example.goride.analytics.repository.MatchingOfferEventRepository;
import com.example.goride.analytics.repository.MatchingRunRepository;
import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.domain.TripStatus;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.matching.telemetry.ExpiredMatchingOffer;
import com.example.goride.matching.telemetry.MatchingTelemetryOfferOutcome;
import com.example.goride.matching.telemetry.MatchingTelemetryPort;
import com.example.goride.matching.telemetry.MatchingTelemetryTrigger;
import com.example.goride.user.domain.User;
import com.example.goride.user.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

@Component
public class JpaMatchingTelemetryAdapter implements MatchingTelemetryPort {
    private static final Set<MatchingOfferOutcome> EXCLUDED_DRIVER_OUTCOMES = EnumSet.of(
            MatchingOfferOutcome.REJECTED,
            MatchingOfferOutcome.TIMEOUT,
            MatchingOfferOutcome.EXPIRED
    );

    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final MatchingRunRepository matchingRunRepository;
    private final MatchingOfferEventRepository matchingOfferEventRepository;

    public JpaMatchingTelemetryAdapter(
            TripRepository tripRepository,
            UserRepository userRepository,
            MatchingRunRepository matchingRunRepository,
            MatchingOfferEventRepository matchingOfferEventRepository
    ) {
        this.tripRepository = tripRepository;
        this.userRepository = userRepository;
        this.matchingRunRepository = matchingRunRepository;
        this.matchingOfferEventRepository = matchingOfferEventRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OptionalInt beginSearch(
            Long tripId,
            MatchingTelemetryTrigger trigger,
            Integer requestedAttempt,
            int candidateCount,
            Instant searchedAt
    ) {
        if (candidateCount < 0) {
            throw new IllegalArgumentException("candidateCount must not be negative");
        }
        if (requestedAttempt != null && requestedAttempt <= 0) {
            throw new IllegalArgumentException("requestedAttempt must be positive");
        }
        Instant normalizedSearchedAt = requireNonNull(searchedAt, "searchedAt");
        Trip trip = requireTripForUpdate(tripId);
        if (trip.getStatus() != TripStatus.SEARCHING) {
            return OptionalInt.empty();
        }
        MatchingRun run = findOrStartRun(trip, trigger, normalizedSearchedAt);

        Optional<MatchingOfferEvent> openOffer = matchingOfferEventRepository
                .findFirstByMatchingRunIdAndOutcomeOrderByAttemptNoDesc(
                        run.getId(),
                        MatchingOfferOutcome.OFFERED
                );
        if (openOffer.isPresent()) {
            return OptionalInt.empty();
        }

        int nextAttempt = requestedAttempt == null
                ? matchingOfferEventRepository.findMaxAttemptNo(run.getId()).orElse(0) + 1
                : requestedAttempt;
        if (matchingOfferEventRepository.findByMatchingRunIdAndAttemptNo(run.getId(), nextAttempt).isPresent()) {
            return OptionalInt.empty();
        }

        run.recordSearch(candidateCount);
        return OptionalInt.of(nextAttempt);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordOffer(
            Long tripId,
            Long driverId,
            int attempt,
            Integer candidateRank,
            double candidateDistanceM,
            Instant offeredAt,
            Instant expiresAt
    ) {
        MatchingRun run = requireOpenRunForUpdate(tripId);
        Optional<MatchingOfferEvent> existing =
                matchingOfferEventRepository.findByRunAndAttemptForUpdate(run.getId(), attempt);
        if (existing.isPresent()) {
            return false;
        }

        User driver = userRepository.findByIdAndDeletedAtIsNull(driverId)
                .orElseThrow(() -> new IllegalStateException("Telemetry driver does not exist: " + driverId));
        MatchingOfferEvent event = MatchingOfferEvent.offer(
                run,
                driver,
                attempt,
                candidateRank,
                candidateDistanceM,
                offeredAt,
                expiresAt
        );
        run.recordOffer();
        matchingOfferEventRepository.save(event);
        return true;
    }

    @Override
    @Transactional
    public void resolveOffer(
            Long tripId,
            int attempt,
            MatchingTelemetryOfferOutcome outcome,
            Instant resolvedAt
    ) {
        MatchingOfferEvent offer = requireOfferForUpdate(tripId, attempt);
        MatchingOfferOutcome target = switch (outcome) {
            case REJECTED -> MatchingOfferOutcome.REJECTED;
            case TIMEOUT -> MatchingOfferOutcome.TIMEOUT;
        };
        if (offer.getOutcome() == target) {
            return;
        }
        ensureOffered(offer, target);
        if (target == MatchingOfferOutcome.REJECTED) {
            offer.reject(resolvedAt);
        } else {
            offer.timeOut(resolvedAt);
        }
    }

    @Override
    @Transactional
    public void acceptOfferAndCompleteRun(
            Long tripId,
            int attempt,
            Long driverId,
            Instant acceptedAt
    ) {
        requireTripForUpdate(tripId);
        Optional<MatchingRun> openRun = matchingRunRepository.findOpenByTripIdForUpdate(tripId);
        if (openRun.isEmpty()) {
            if (isAlreadyAccepted(tripId, attempt, driverId)) {
                return;
            }
            throw new IllegalStateException("Open matching run telemetry is missing");
        }
        MatchingRun run = openRun.get();
        MatchingOfferEvent offer = matchingOfferEventRepository
                .findByRunAndAttemptForUpdate(run.getId(), attempt)
                .orElseThrow(() -> new IllegalStateException("Matching offer telemetry is missing"));
        if (offer.getOutcome() == MatchingOfferOutcome.ACCEPTED
                && run.getOutcome() == MatchingRunOutcome.MATCHED) {
            return;
        }
        ensureOffered(offer, MatchingOfferOutcome.ACCEPTED);
        if (!offer.getDriver().getId().equals(driverId)) {
            throw new IllegalStateException("Accepted driver does not own the telemetry offer");
        }
        User driver = userRepository.findByIdAndDeletedAtIsNull(driverId)
                .orElseThrow(() -> new IllegalStateException("Telemetry driver does not exist: " + driverId));
        offer.accept(acceptedAt);
        run.markMatched(driver, acceptedAt);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireOffer(Long tripId, int attempt, Instant lateResponseAt) {
        MatchingOfferEvent offer = requireOfferForUpdate(tripId, attempt);
        if (offer.getOutcome() == MatchingOfferOutcome.EXPIRED) {
            return;
        }
        ensureOffered(offer, MatchingOfferOutcome.EXPIRED);
        offer.expire(lateResponseAt);
    }

    @Override
    @Transactional
    public void cancelRun(Long tripId, Instant cancelledAt) {
        Optional<MatchingRun> openRun = matchingRunRepository.findOpenByTripIdForUpdate(tripId);
        if (openRun.isEmpty()) {
            return;
        }
        MatchingRun run = openRun.get();
        matchingOfferEventRepository
                .findFirstByMatchingRunIdAndOutcomeOrderByAttemptNoDesc(
                        run.getId(),
                        MatchingOfferOutcome.OFFERED
                )
                .ifPresent(offer -> offer.cancel(cancelledAt));
        run.cancel(cancelledAt);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExpiredMatchingOffer> findExpiredOffers(Instant cutoff, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return matchingOfferEventRepository
                .findExpiredOpenOffers(cutoff, PageRequest.of(0, limit))
                .stream()
                .map(this::toExpiredOffer)
                .toList();
    }

    private MatchingRun findOrStartRun(
            Trip trip,
            MatchingTelemetryTrigger trigger,
            Instant startedAt
    ) {
        return matchingRunRepository.findOpenByTripIdForUpdate(trip.getId())
                .orElseGet(() -> matchingRunRepository.saveAndFlush(MatchingRun.start(
                        trip,
                        MatchingTriggerType.valueOf(trigger.name()),
                        startedAt
                )));
    }

    private MatchingRun requireOpenRunForUpdate(Long tripId) {
        requireTripForUpdate(tripId);
        return matchingRunRepository.findOpenByTripIdForUpdate(tripId)
                .orElseThrow(() -> new IllegalStateException("Open matching run telemetry is missing"));
    }

    private boolean isAlreadyAccepted(Long tripId, int attempt, Long driverId) {
        Optional<MatchingRun> latestRun =
                matchingRunRepository.findFirstByTripIdOrderByStartedAtDescIdDesc(tripId);
        if (latestRun.isEmpty()
                || latestRun.get().getOutcome() != MatchingRunOutcome.MATCHED
                || !latestRun.get().getMatchedDriver().getId().equals(driverId)) {
            return false;
        }
        return matchingOfferEventRepository
                .findByRunAndAttemptForUpdate(latestRun.get().getId(), attempt)
                .filter(offer -> offer.getOutcome() == MatchingOfferOutcome.ACCEPTED)
                .filter(offer -> offer.getDriver().getId().equals(driverId))
                .isPresent();
    }

    private MatchingOfferEvent requireOfferForUpdate(Long tripId, int attempt) {
        MatchingRun run = requireOpenRunForUpdate(tripId);
        return matchingOfferEventRepository.findByRunAndAttemptForUpdate(run.getId(), attempt)
                .orElseThrow(() -> new IllegalStateException("Matching offer telemetry is missing"));
    }

    private Trip requireTripForUpdate(Long tripId) {
        return tripRepository.findActiveByIdForUpdate(tripId)
                .orElseThrow(() -> new IllegalStateException("Telemetry trip does not exist: " + tripId));
    }

    private ExpiredMatchingOffer toExpiredOffer(MatchingOfferEvent offer) {
        Set<Long> excludedDrivers = new LinkedHashSet<>(
                matchingOfferEventRepository.findDriverIdsByRunAndOutcomeIn(
                        offer.getMatchingRun().getId(),
                        EXCLUDED_DRIVER_OUTCOMES
                )
        );
        excludedDrivers.add(offer.getDriver().getId());
        return new ExpiredMatchingOffer(
                offer.getMatchingRun().getTrip().getId(),
                offer.getDriver().getId(),
                offer.getAttemptNo(),
                offer.getExpiresAt(),
                excludedDrivers
        );
    }

    private void ensureOffered(MatchingOfferEvent offer, MatchingOfferOutcome target) {
        if (offer.getOutcome() != MatchingOfferOutcome.OFFERED) {
            throw new IllegalStateException(
                    "Offer is already " + offer.getOutcome() + " and cannot become " + target
            );
        }
    }

    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }
}
