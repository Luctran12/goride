package com.example.goride.analytics.domain;

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
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Check;

import java.time.Instant;

@Entity
@Table(
        name = "matching_offer_events",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_matching_offer_events_run_attempt",
                        columnNames = {"matching_run_id", "attempt_no"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_matching_offer_events_driver_offered_at",
                        columnList = "driver_id, offered_at"
                ),
                @Index(
                        name = "idx_matching_offer_events_outcome_offered_at",
                        columnList = "outcome, offered_at"
                )
        }
)
@Check(
        name = "chk_matching_offer_events_entity",
        constraints = """
                attempt_no > 0
                AND (candidate_rank IS NULL OR candidate_rank > 0)
                AND (candidate_distance_m IS NULL OR candidate_distance_m >= 0)
                AND expires_at > offered_at
                AND (responded_at IS NULL OR responded_at >= offered_at)
                AND (
                    (outcome = 'OFFERED' AND responded_at IS NULL)
                    OR
                    (outcome IN ('ACCEPTED', 'REJECTED')
                        AND responded_at IS NOT NULL
                        AND responded_at <= expires_at)
                    OR
                    (outcome = 'TIMEOUT' AND responded_at IS NULL)
                    OR
                    (outcome = 'CANCELLED' AND responded_at IS NOT NULL)
                    OR
                    (outcome = 'EXPIRED'
                        AND responded_at IS NOT NULL
                        AND responded_at >= expires_at)
                )
                """
)
public class MatchingOfferEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "matching_run_id", nullable = false, updatable = false)
    private MatchingRun matchingRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false, updatable = false)
    private User driver;

    @Column(name = "attempt_no", nullable = false, updatable = false)
    private int attemptNo;

    @Column(name = "candidate_rank", updatable = false)
    private Integer candidateRank;

    @Column(name = "candidate_distance_m", updatable = false)
    private Double candidateDistanceM;

    @Column(name = "offered_at", nullable = false, updatable = false)
    private Instant offeredAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MatchingOfferOutcome outcome;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MatchingOfferEvent() {
    }

    public static MatchingOfferEvent offer(
            MatchingRun matchingRun,
            User driver,
            int attemptNo,
            Integer candidateRank,
            Double candidateDistanceM,
            Instant offeredAt,
            Instant expiresAt
    ) {
        MatchingRun normalizedRun = requireNonNull(matchingRun, "matchingRun");
        if (!normalizedRun.isInProgress()) {
            throw new IllegalArgumentException("matchingRun must be in progress");
        }
        User normalizedDriver = requireNonNull(driver, "driver");
        if (!normalizedDriver.hasRole(UserRole.DRIVER)) {
            throw new IllegalArgumentException("Offered user must have DRIVER role");
        }
        if (attemptNo <= 0) {
            throw new IllegalArgumentException("attemptNo must be positive");
        }
        if (candidateRank != null && candidateRank <= 0) {
            throw new IllegalArgumentException("candidateRank must be positive");
        }
        if (candidateDistanceM != null
                && (!Double.isFinite(candidateDistanceM) || candidateDistanceM < 0)) {
            throw new IllegalArgumentException("candidateDistanceM must be finite and not negative");
        }
        Instant normalizedOfferedAt = requireNonNull(offeredAt, "offeredAt");
        Instant normalizedExpiresAt = requireNonNull(expiresAt, "expiresAt");
        if (!normalizedExpiresAt.isAfter(normalizedOfferedAt)) {
            throw new IllegalArgumentException("expiresAt must be after offeredAt");
        }

        MatchingOfferEvent event = new MatchingOfferEvent();
        event.matchingRun = normalizedRun;
        event.driver = normalizedDriver;
        event.attemptNo = attemptNo;
        event.candidateRank = candidateRank;
        event.candidateDistanceM = candidateDistanceM;
        event.offeredAt = normalizedOfferedAt;
        event.expiresAt = normalizedExpiresAt;
        event.outcome = MatchingOfferOutcome.OFFERED;
        return event;
    }

    public void accept(Instant respondedAt) {
        resolveResponse(MatchingOfferOutcome.ACCEPTED, respondedAt, false);
    }

    public void reject(Instant respondedAt) {
        resolveResponse(MatchingOfferOutcome.REJECTED, respondedAt, false);
    }

    public void timeOut(Instant timedOutAt) {
        ensureOffered();
        Instant normalizedTimedOutAt = requireNonNull(timedOutAt, "timedOutAt");
        if (normalizedTimedOutAt.isBefore(expiresAt)) {
            throw new IllegalArgumentException("timedOutAt must not be before expiresAt");
        }
        outcome = MatchingOfferOutcome.TIMEOUT;
        respondedAt = null;
    }

    public void cancel(Instant cancelledAt) {
        resolveResponse(MatchingOfferOutcome.CANCELLED, cancelledAt, true);
    }

    public void expire(Instant lateResponseAt) {
        ensureOffered();
        Instant normalizedLateResponseAt = requireNonNull(lateResponseAt, "lateResponseAt");
        if (normalizedLateResponseAt.isBefore(expiresAt)) {
            throw new IllegalArgumentException("lateResponseAt must not be before expiresAt");
        }
        outcome = MatchingOfferOutcome.EXPIRED;
        respondedAt = normalizedLateResponseAt;
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

    private void resolveResponse(
            MatchingOfferOutcome terminalOutcome,
            Instant responseAt,
            boolean allowAfterExpiry
    ) {
        ensureOffered();
        Instant normalizedResponseAt = requireNonNull(responseAt, "responseAt");
        if (normalizedResponseAt.isBefore(offeredAt)) {
            throw new IllegalArgumentException("responseAt must not be before offeredAt");
        }
        if (!allowAfterExpiry && normalizedResponseAt.isAfter(expiresAt)) {
            throw new IllegalArgumentException("responseAt must not be after expiresAt");
        }
        outcome = terminalOutcome;
        respondedAt = normalizedResponseAt;
    }

    private void ensureOffered() {
        if (outcome != MatchingOfferOutcome.OFFERED) {
            throw new IllegalStateException("Matching offer is already terminal");
        }
    }

    private void validateState() {
        requireNonNull(matchingRun, "matchingRun");
        requireNonNull(driver, "driver");
        requireNonNull(offeredAt, "offeredAt");
        requireNonNull(expiresAt, "expiresAt");
        requireNonNull(outcome, "outcome");
        if (attemptNo <= 0 || !expiresAt.isAfter(offeredAt)) {
            throw new IllegalStateException("Matching offer identity or time range is invalid");
        }
        if (outcome == MatchingOfferOutcome.OFFERED || outcome == MatchingOfferOutcome.TIMEOUT) {
            if (respondedAt != null) {
                throw new IllegalStateException("Offered and timed-out events cannot contain respondedAt");
            }
            return;
        }
        if (respondedAt == null || respondedAt.isBefore(offeredAt)) {
            throw new IllegalStateException("Resolved offer requires a valid respondedAt");
        }
        if ((outcome == MatchingOfferOutcome.ACCEPTED || outcome == MatchingOfferOutcome.REJECTED)
                && respondedAt.isAfter(expiresAt)) {
            throw new IllegalStateException("Accepted or rejected offer cannot resolve after expiry");
        }
        if (outcome == MatchingOfferOutcome.EXPIRED && respondedAt.isBefore(expiresAt)) {
            throw new IllegalStateException("Expired offer requires a late response");
        }
    }

    public Long getId() {
        return id;
    }

    public MatchingRun getMatchingRun() {
        return matchingRun;
    }

    public User getDriver() {
        return driver;
    }

    public int getAttemptNo() {
        return attemptNo;
    }

    public Integer getCandidateRank() {
        return candidateRank;
    }

    public Double getCandidateDistanceM() {
        return candidateDistanceM;
    }

    public Instant getOfferedAt() {
        return offeredAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public MatchingOfferOutcome getOutcome() {
        return outcome;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }
}
