package com.example.goride.analytics.domain;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.booking.domain.PricingConfig;
import com.example.goride.booking.domain.Trip;
import com.example.goride.driver.domain.VehicleType;
import com.example.goride.user.domain.User;
import com.example.goride.user.domain.UserRole;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalyticsTelemetryDomainTests {
    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);
    private static final Instant STARTED_AT = Instant.parse("2026-07-28T01:00:00Z");

    @Test
    void matchingRunAccumulatesSearchAndOfferCounters() {
        MatchingRun run = MatchingRun.start(
                trip(),
                MatchingTriggerType.BOOKING_CREATED,
                STARTED_AT
        );

        run.recordSearch(4);
        run.recordSearch(2);
        run.recordOffer();

        assertThat(run.getOutcome()).isEqualTo(MatchingRunOutcome.IN_PROGRESS);
        assertThat(run.getSearchCount()).isEqualTo(2);
        assertThat(run.getCandidateCount()).isEqualTo(6);
        assertThat(run.getOfferCount()).isEqualTo(1);
    }

    @Test
    void matchingRunRequiresDriverForMatchedOutcomeAndBecomesTerminalOnce() {
        MatchingRun run = MatchingRun.start(
                trip(),
                MatchingTriggerType.BOOKING_CREATED,
                STARTED_AT
        );
        User driver = driver();

        run.markMatched(driver, STARTED_AT.plusSeconds(20));

        assertThat(run.getOutcome()).isEqualTo(MatchingRunOutcome.MATCHED);
        assertThat(run.getMatchedDriver()).isSameAs(driver);
        assertThat(run.getFinishedAt()).isEqualTo(STARTED_AT.plusSeconds(20));
        assertThatThrownBy(() -> run.markNoDriver(STARTED_AT.plusSeconds(30)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Matching run is already terminal");
    }

    @Test
    void failedMatchingRunRequiresNormalizedReasonCode() {
        MatchingRun run = MatchingRun.start(
                trip(),
                MatchingTriggerType.RECOVERY,
                STARTED_AT
        );

        run.fail("  REDIS_STATE_MISSING  ", STARTED_AT.plusSeconds(5));

        assertThat(run.getOutcome()).isEqualTo(MatchingRunOutcome.FAILED);
        assertThat(run.getFailureReasonCode()).isEqualTo("REDIS_STATE_MISSING");
    }

    @Test
    void matchingRunRejectsTerminalTimeBeforeStart() {
        MatchingRun run = MatchingRun.start(
                trip(),
                MatchingTriggerType.BOOKING_CREATED,
                STARTED_AT
        );

        assertThatThrownBy(() -> run.markNoDriver(STARTED_AT.minusMillis(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finishedAt must not be before startedAt");
    }

    @Test
    void offerCapturesCandidateAndAcceptsWithinExpiry() {
        MatchingRun run = MatchingRun.start(
                trip(),
                MatchingTriggerType.BOOKING_CREATED,
                STARTED_AT
        );
        MatchingOfferEvent offer = MatchingOfferEvent.offer(
                run,
                driver(),
                1,
                2,
                735.5,
                STARTED_AT.plusSeconds(2),
                STARTED_AT.plusSeconds(32)
        );

        offer.accept(STARTED_AT.plusSeconds(12));

        assertThat(offer.getOutcome()).isEqualTo(MatchingOfferOutcome.ACCEPTED);
        assertThat(offer.getAttemptNo()).isEqualTo(1);
        assertThat(offer.getCandidateRank()).isEqualTo(2);
        assertThat(offer.getCandidateDistanceM()).isEqualTo(735.5);
        assertThat(offer.getRespondedAt()).isEqualTo(STARTED_AT.plusSeconds(12));
    }

    @Test
    void offerClassifiesLateResponseAsExpired() {
        MatchingRun run = MatchingRun.start(
                trip(),
                MatchingTriggerType.BOOKING_CREATED,
                STARTED_AT
        );
        MatchingOfferEvent offer = MatchingOfferEvent.offer(
                run,
                driver(),
                1,
                1,
                100.0,
                STARTED_AT,
                STARTED_AT.plusSeconds(30)
        );

        assertThatThrownBy(() -> offer.accept(STARTED_AT.plusSeconds(31)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("responseAt must not be after expiresAt");

        offer.expire(STARTED_AT.plusSeconds(31));

        assertThat(offer.getOutcome()).isEqualTo(MatchingOfferOutcome.EXPIRED);
        assertThat(offer.getRespondedAt()).isEqualTo(STARTED_AT.plusSeconds(31));
    }

    @Test
    void offerCanTimeOutOnlyAtOrAfterExpiry() {
        MatchingRun run = MatchingRun.start(
                trip(),
                MatchingTriggerType.BOOKING_CREATED,
                STARTED_AT
        );
        MatchingOfferEvent offer = MatchingOfferEvent.offer(
                run,
                driver(),
                1,
                1,
                100.0,
                STARTED_AT,
                STARTED_AT.plusSeconds(30)
        );

        assertThatThrownBy(() -> offer.timeOut(STARTED_AT.plusSeconds(29)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("timedOutAt must not be before expiresAt");

        offer.timeOut(STARTED_AT.plusSeconds(30));

        assertThat(offer.getOutcome()).isEqualTo(MatchingOfferOutcome.TIMEOUT);
        assertThat(offer.getRespondedAt()).isNull();
    }

    @Test
    void offerRejectsInvalidIdentityAndDistance() {
        MatchingRun run = MatchingRun.start(
                trip(),
                MatchingTriggerType.BOOKING_CREATED,
                STARTED_AT
        );

        assertThatThrownBy(() -> MatchingOfferEvent.offer(
                run,
                driver(),
                0,
                1,
                Double.NaN,
                STARTED_AT,
                STARTED_AT.plusSeconds(30)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("attemptNo must be positive");

        assertThatThrownBy(() -> MatchingOfferEvent.offer(
                run,
                driver(),
                1,
                1,
                Double.POSITIVE_INFINITY,
                STARTED_AT,
                STARTED_AT.plusSeconds(30)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("candidateDistanceM must be finite and not negative");
    }

    @Test
    void supplySnapshotRejectsImpossibleCountsAndSamplingTime() {
        assertThatThrownBy(() -> DriverSupplySnapshot.record(
                STARTED_AT,
                null,
                VehicleType.MOTORBIKE,
                3,
                2,
                2,
                STARTED_AT
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("availableDrivers plus busyDrivers must not exceed onlineDrivers");

        assertThatThrownBy(() -> DriverSupplySnapshot.record(
                STARTED_AT,
                null,
                VehicleType.MOTORBIKE,
                4,
                2,
                2,
                STARTED_AT.minusMillis(1)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sampledAt must not be before bucketStart");
    }

    private Trip trip() {
        PricingConfig pricingConfig = PricingConfig.create(
                VehicleType.MOTORBIKE,
                BigDecimal.valueOf(10000),
                BigDecimal.valueOf(4000),
                BigDecimal.valueOf(300),
                BigDecimal.valueOf(15000),
                BigDecimal.ONE,
                Instant.parse("2026-01-01T00:00:00Z")
        );
        return Trip.create(
                passenger(),
                VehicleType.MOTORBIKE,
                PaymentMethod.CASH,
                "Ben Thanh Market",
                point(106.7000, 10.7700),
                "Tan Son Nhat Airport",
                point(106.6650, 10.8130),
                BigDecimal.valueOf(4.2),
                18,
                BigDecimal.valueOf(32000),
                pricingConfig
        );
    }

    private User passenger() {
        return User.create(
                "Passenger",
                "0900000000",
                null,
                "hash",
                Set.of(UserRole.PASSENGER)
        );
    }

    private User driver() {
        return User.create(
                "Driver",
                "0900000001",
                null,
                "hash",
                Set.of(UserRole.DRIVER)
        );
    }

    private static org.locationtech.jts.geom.Point point(double longitude, double latitude) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
    }
}
