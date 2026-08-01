package com.example.goride.analytics.domain;

import com.example.goride.booking.domain.Trip;
import com.example.goride.user.domain.User;
import com.example.goride.user.domain.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.Check;

import java.time.Instant;

@Entity
@Table(
        name = "matching_runs",
        indexes = {
                @Index(name = "idx_matching_runs_trip", columnList = "trip_id"),
                @Index(name = "idx_matching_runs_started_at", columnList = "started_at"),
                @Index(name = "idx_matching_runs_outcome_started_at", columnList = "outcome, started_at")
        }
)
@Check(
        name = "chk_matching_runs_entity",
        constraints = """
                search_count >= 0
                AND candidate_count >= 0
                AND offer_count >= 0
                AND (finished_at IS NULL OR finished_at >= started_at)
                AND (
                    (outcome = 'IN_PROGRESS'
                        AND finished_at IS NULL
                        AND matched_driver_id IS NULL
                        AND failure_reason_code IS NULL)
                    OR
                    (outcome = 'MATCHED'
                        AND finished_at IS NOT NULL
                        AND matched_driver_id IS NOT NULL
                        AND failure_reason_code IS NULL)
                    OR
                    (outcome IN ('NO_DRIVER', 'CANCELLED')
                        AND finished_at IS NOT NULL
                        AND matched_driver_id IS NULL
                        AND failure_reason_code IS NULL)
                    OR
                    (outcome = 'FAILED'
                        AND finished_at IS NOT NULL
                        AND matched_driver_id IS NULL
                        AND failure_reason_code IS NOT NULL)
                )
                """
)
public class MatchingRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MatchingRunOutcome outcome;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "matched_driver_id")
    private User matchedDriver;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 30, updatable = false)
    private MatchingTriggerType triggerType;

    @Column(name = "search_count", nullable = false)
    private int searchCount;

    @Column(name = "candidate_count", nullable = false)
    private int candidateCount;

    @Column(name = "offer_count", nullable = false)
    private int offerCount;

    @Column(name = "failure_reason_code", length = 50)
    private String failureReasonCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MatchingRun() {
    }

    public static MatchingRun start(Trip trip, MatchingTriggerType triggerType, Instant startedAt) {
        MatchingRun run = new MatchingRun();
        run.trip = requireNonNull(trip, "trip");
        run.triggerType = requireNonNull(triggerType, "triggerType");
        run.startedAt = requireNonNull(startedAt, "startedAt");
        run.outcome = MatchingRunOutcome.IN_PROGRESS;
        return run;
    }

    public void recordSearch(int candidatesFound) {
        ensureInProgress();
        if (candidatesFound < 0) {
            throw new IllegalArgumentException("candidatesFound must not be negative");
        }
        searchCount = Math.addExact(searchCount, 1);
        candidateCount = Math.addExact(candidateCount, candidatesFound);
    }

    public void recordOffer() {
        ensureInProgress();
        offerCount = Math.addExact(offerCount, 1);
    }

    public void markMatched(User driver, Instant finishedAt) {
        User normalizedDriver = requireNonNull(driver, "driver");
        if (!normalizedDriver.hasRole(UserRole.DRIVER)) {
            throw new IllegalArgumentException("Matched user must have DRIVER role");
        }
        complete(MatchingRunOutcome.MATCHED, normalizedDriver, null, finishedAt);
    }

    public void markNoDriver(Instant finishedAt) {
        complete(MatchingRunOutcome.NO_DRIVER, null, null, finishedAt);
    }

    public void cancel(Instant finishedAt) {
        complete(MatchingRunOutcome.CANCELLED, null, null, finishedAt);
    }

    public void fail(String reasonCode, Instant finishedAt) {
        complete(
                MatchingRunOutcome.FAILED,
                null,
                requireReasonCode(reasonCode),
                finishedAt
        );
    }

    @PrePersist
    void prePersist() {
        validateState();
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        validateState();
        updatedAt = Instant.now();
    }

    private void complete(
            MatchingRunOutcome terminalOutcome,
            User matchedDriver,
            String failureReasonCode,
            Instant finishedAt
    ) {
        ensureInProgress();
        if (!terminalOutcome.isTerminal()) {
            throw new IllegalArgumentException("terminalOutcome must be terminal");
        }
        Instant normalizedFinishedAt = requireNonNull(finishedAt, "finishedAt");
        if (normalizedFinishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("finishedAt must not be before startedAt");
        }
        this.outcome = terminalOutcome;
        this.matchedDriver = matchedDriver;
        this.failureReasonCode = failureReasonCode;
        this.finishedAt = normalizedFinishedAt;
        validateState();
    }

    private void ensureInProgress() {
        if (outcome != MatchingRunOutcome.IN_PROGRESS) {
            throw new IllegalStateException("Matching run is already terminal");
        }
    }

    private void validateState() {
        requireNonNull(trip, "trip");
        requireNonNull(startedAt, "startedAt");
        requireNonNull(triggerType, "triggerType");
        requireNonNull(outcome, "outcome");
        if (searchCount < 0 || candidateCount < 0 || offerCount < 0) {
            throw new IllegalStateException("Matching counters must not be negative");
        }
        if (outcome == MatchingRunOutcome.IN_PROGRESS) {
            if (finishedAt != null || matchedDriver != null || failureReasonCode != null) {
                throw new IllegalStateException("In-progress run cannot contain terminal fields");
            }
            return;
        }
        if (finishedAt == null || finishedAt.isBefore(startedAt)) {
            throw new IllegalStateException("Terminal run must have a valid finishedAt");
        }
        if (outcome == MatchingRunOutcome.MATCHED) {
            if (matchedDriver == null || failureReasonCode != null) {
                throw new IllegalStateException("Matched run requires only matchedDriver");
            }
            return;
        }
        if (matchedDriver != null) {
            throw new IllegalStateException("Only matched runs can contain matchedDriver");
        }
        if (outcome == MatchingRunOutcome.FAILED) {
            if (failureReasonCode == null) {
                throw new IllegalStateException("Failed run requires failureReasonCode");
            }
        } else if (failureReasonCode != null) {
            throw new IllegalStateException("Only failed runs can contain failureReasonCode");
        }
    }

    public Long getId() {
        return id;
    }

    public Trip getTrip() {
        return trip;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public MatchingRunOutcome getOutcome() {
        return outcome;
    }

    public User getMatchedDriver() {
        return matchedDriver;
    }

    public MatchingTriggerType getTriggerType() {
        return triggerType;
    }

    public int getSearchCount() {
        return searchCount;
    }

    public int getCandidateCount() {
        return candidateCount;
    }

    public int getOfferCount() {
        return offerCount;
    }

    public String getFailureReasonCode() {
        return failureReasonCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isInProgress() {
        return outcome == MatchingRunOutcome.IN_PROGRESS;
    }

    private static String requireReasonCode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("reasonCode must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > 50) {
            throw new IllegalArgumentException("reasonCode must not exceed 50 characters");
        }
        return normalized;
    }

    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }
}
