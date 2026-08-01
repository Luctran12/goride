package com.example.goride.integration;

import com.example.goride.analytics.domain.DriverSupplySnapshot;
import com.example.goride.analytics.domain.MatchingOfferEvent;
import com.example.goride.analytics.domain.MatchingOfferOutcome;
import com.example.goride.analytics.domain.MatchingRun;
import com.example.goride.analytics.domain.MatchingRunOutcome;
import com.example.goride.analytics.domain.MatchingTriggerType;
import com.example.goride.analytics.repository.DriverSupplySnapshotRepository;
import com.example.goride.analytics.repository.MatchingOfferEventRepository;
import com.example.goride.analytics.repository.MatchingRunRepository;
import com.example.goride.matching.telemetry.MatchingTelemetryOfferOutcome;
import com.example.goride.matching.telemetry.MatchingTelemetryPort;
import com.example.goride.matching.telemetry.MatchingTelemetryTrigger;
import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.booking.domain.PricingConfig;
import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.repository.PricingConfigRepository;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.driver.domain.VehicleType;
import com.example.goride.user.domain.User;
import com.example.goride.user.domain.UserRole;
import com.example.goride.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminAnalyticsTelemetryRepositoryIntegrationTests extends PostgresRedisIntegrationTest {
    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PricingConfigRepository pricingConfigRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private MatchingRunRepository matchingRunRepository;

    @Autowired
    private MatchingOfferEventRepository matchingOfferEventRepository;

    @Autowired
    private DriverSupplySnapshotRepository driverSupplySnapshotRepository;

    @Autowired
    private MatchingTelemetryPort matchingTelemetry;

    @BeforeAll
    void applyReleaseSql() throws IOException {
        jdbcTemplate.execute("""
                DROP TABLE IF EXISTS matching_offer_events;
                DROP TABLE IF EXISTS driver_supply_snapshots;
                DROP TABLE IF EXISTS matching_runs;
                """);
        jdbcTemplate.execute(Files.readString(Path.of(
                "db",
                "releases",
                "20260727-admin-analytics-telemetry",
                "precheck.sql"
        )));
        jdbcTemplate.execute(Files.readString(Path.of(
                "db",
                "releases",
                "20260727-admin-analytics-telemetry",
                "apply.sql"
        )));
        jdbcTemplate.execute(Files.readString(Path.of(
                "db",
                "releases",
                "20260727-admin-analytics-telemetry",
                "verify.sql"
        )));
    }

    @Test
    void repositoriesPersistAndFindTelemetry() {
        Seed seed = seed();
        Instant startedAt = Instant.parse("2026-07-28T01:00:00Z");
        MatchingRun run = matchingRunRepository.saveAndFlush(MatchingRun.start(
                seed.trip(),
                MatchingTriggerType.BOOKING_CREATED,
                startedAt
        ));
        MatchingOfferEvent offer = matchingOfferEventRepository.saveAndFlush(
                MatchingOfferEvent.offer(
                        run,
                        seed.driver(),
                        1,
                        1,
                        425.75,
                        startedAt.plusSeconds(2),
                        startedAt.plusSeconds(32)
                )
        );
        DriverSupplySnapshot snapshot = driverSupplySnapshotRepository.saveAndFlush(
                DriverSupplySnapshot.record(
                        Instant.parse("2026-07-28T01:00:00Z"),
                        null,
                        VehicleType.MOTORBIKE,
                        10,
                        6,
                        3,
                        Instant.parse("2026-07-28T01:00:05Z")
                )
        );

        assertThat(matchingRunRepository.findByTripIdAndOutcome(
                seed.trip().getId(),
                MatchingRunOutcome.IN_PROGRESS
        )).get()
                .extracting(MatchingRun::getId)
                .isEqualTo(run.getId());
        MatchingRun lockedRun = transactionTemplate.execute(status ->
                matchingRunRepository.findOpenByTripIdForUpdate(seed.trip().getId()).orElseThrow()
        );
        assertThat(lockedRun.getId()).isEqualTo(run.getId());
        assertThat(matchingOfferEventRepository.findByMatchingRunIdAndAttemptNo(
                run.getId(),
                1
        )).get()
                .extracting(MatchingOfferEvent::getId)
                .isEqualTo(offer.getId());
        MatchingOfferEvent lockedOffer = transactionTemplate.execute(status ->
                matchingOfferEventRepository
                        .findFirstByMatchingRunIdAndOutcomeOrderByAttemptNoDesc(
                                run.getId(),
                                MatchingOfferOutcome.OFFERED
                        )
                        .orElseThrow()
        );
        assertThat(lockedOffer.getId()).isEqualTo(offer.getId());
        assertThat(driverSupplySnapshotRepository
                .findByBucketStartAndServiceAreaIsNullAndVehicleType(
                        snapshot.getBucketStart(),
                        VehicleType.MOTORBIKE
                )).get()
                .extracting(DriverSupplySnapshot::getId)
                .isEqualTo(snapshot.getId());
    }

    @Test
    void databaseRejectsTwoOpenRunsForOneTrip() {
        Seed seed = seed();
        Instant startedAt = Instant.parse("2026-07-28T02:00:00Z");

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            matchingRunRepository.saveAndFlush(MatchingRun.start(
                    seed.trip(),
                    MatchingTriggerType.BOOKING_CREATED,
                    startedAt
            ));
            matchingRunRepository.saveAndFlush(MatchingRun.start(
                    seed.trip(),
                    MatchingTriggerType.RECOVERY,
                    startedAt.plusSeconds(1)
            ));
        })).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsDuplicateGlobalSupplyBucket() {
        Instant bucketStart = Instant.parse("2026-07-28T03:00:00Z");

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            driverSupplySnapshotRepository.saveAndFlush(DriverSupplySnapshot.record(
                    bucketStart,
                    null,
                    VehicleType.CAR_4_SEAT,
                    4,
                    2,
                    1,
                    bucketStart.plusSeconds(1)
            ));
            driverSupplySnapshotRepository.saveAndFlush(DriverSupplySnapshot.record(
                    bucketStart,
                    null,
                    VehicleType.CAR_4_SEAT,
                    5,
                    3,
                    1,
                    bucketStart.plusSeconds(2)
            ));
        })).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsDuplicateOfferAttemptWithinRun() {
        Seed seed = seed();
        Instant startedAt = Instant.parse("2026-07-28T03:30:00Z");
        MatchingRun run = matchingRunRepository.saveAndFlush(MatchingRun.start(
                seed.trip(),
                MatchingTriggerType.BOOKING_CREATED,
                startedAt
        ));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            matchingOfferEventRepository.saveAndFlush(MatchingOfferEvent.offer(
                    run,
                    seed.driver(),
                    1,
                    1,
                    100.0,
                    startedAt,
                    startedAt.plusSeconds(30)
            ));
            matchingOfferEventRepository.saveAndFlush(MatchingOfferEvent.offer(
                    run,
                    seed.driver(),
                    1,
                    2,
                    150.0,
                    startedAt.plusSeconds(1),
                    startedAt.plusSeconds(31)
            ));
        })).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsInvalidMatchedTerminalState() {
        Seed seed = seed();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO matching_runs (
                    trip_id,
                    started_at,
                    finished_at,
                    outcome,
                    matched_driver_id,
                    trigger_type,
                    search_count,
                    candidate_count,
                    offer_count,
                    failure_reason_code,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, 'MATCHED', NULL, 'BOOKING_CREATED', 1, 1, 1, NULL, ?, ?)
                """,
                seed.trip().getId(),
                Timestamp.from(Instant.parse("2026-07-28T04:00:00Z")),
                Timestamp.from(Instant.parse("2026-07-28T04:00:10Z")),
                Timestamp.from(Instant.parse("2026-07-28T04:00:00Z")),
                Timestamp.from(Instant.parse("2026-07-28T04:00:10Z"))
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void telemetryAdapterKeepsRetriesInOneRunAndResolvesIdempotently() {
        Seed seed = seed();
        Instant startedAt = Instant.parse("2026-07-28T05:00:00Z");

        assertThat(matchingTelemetry.beginSearch(
                seed.trip().getId(),
                MatchingTelemetryTrigger.BOOKING_CREATED,
                null,
                3,
                startedAt
        )).hasValue(1);
        assertThat(matchingTelemetry.recordOffer(
                seed.trip().getId(),
                seed.driver().getId(),
                1,
                1,
                250.0,
                startedAt.plusSeconds(1),
                startedAt.plusSeconds(31)
        )).isTrue();
        assertThat(matchingTelemetry.recordOffer(
                seed.trip().getId(),
                seed.driver().getId(),
                1,
                1,
                250.0,
                startedAt.plusSeconds(1),
                startedAt.plusSeconds(31)
        )).isFalse();

        matchingTelemetry.resolveOffer(
                seed.trip().getId(),
                1,
                MatchingTelemetryOfferOutcome.REJECTED,
                startedAt.plusSeconds(5)
        );
        matchingTelemetry.resolveOffer(
                seed.trip().getId(),
                1,
                MatchingTelemetryOfferOutcome.REJECTED,
                startedAt.plusSeconds(5)
        );

        assertThat(matchingTelemetry.beginSearch(
                seed.trip().getId(),
                MatchingTelemetryTrigger.RECOVERY,
                2,
                1,
                startedAt.plusSeconds(6)
        )).hasValue(2);
        assertThat(matchingTelemetry.recordOffer(
                seed.trip().getId(),
                seed.driver().getId(),
                2,
                1,
                175.0,
                startedAt.plusSeconds(7),
                startedAt.plusSeconds(37)
        )).isTrue();
        matchingTelemetry.acceptOfferAndCompleteRun(
                seed.trip().getId(),
                2,
                seed.driver().getId(),
                startedAt.plusSeconds(12)
        );
        matchingTelemetry.acceptOfferAndCompleteRun(
                seed.trip().getId(),
                2,
                seed.driver().getId(),
                startedAt.plusSeconds(12)
        );

        MatchingRun run = matchingRunRepository.findByTripIdAndOutcome(
                seed.trip().getId(),
                MatchingRunOutcome.MATCHED
        ).orElseThrow();
        assertThat(run.getTriggerType()).isEqualTo(MatchingTriggerType.BOOKING_CREATED);
        assertThat(run.getSearchCount()).isEqualTo(2);
        assertThat(run.getCandidateCount()).isEqualTo(4);
        assertThat(run.getOfferCount()).isEqualTo(2);
        assertThat(run.getMatchedDriver().getId()).isEqualTo(seed.driver().getId());
        assertThat(matchingOfferEventRepository
                .findByMatchingRunIdAndAttemptNo(run.getId(), 1)
                .orElseThrow()
                .getOutcome()).isEqualTo(MatchingOfferOutcome.REJECTED);
        assertThat(matchingOfferEventRepository
                .findByMatchingRunIdAndAttemptNo(run.getId(), 2)
                .orElseThrow()
                .getOutcome()).isEqualTo(MatchingOfferOutcome.ACCEPTED);
    }

    @Test
    void telemetryAdapterFindsExpiredOfferForDatabaseRecovery() {
        Seed seed = seed();
        Instant startedAt = Instant.parse("2026-07-28T06:00:00Z");
        matchingTelemetry.beginSearch(
                seed.trip().getId(),
                MatchingTelemetryTrigger.BOOKING_CREATED,
                null,
                1,
                startedAt
        );
        matchingTelemetry.recordOffer(
                seed.trip().getId(),
                seed.driver().getId(),
                1,
                1,
                100.0,
                startedAt.plusSeconds(1),
                startedAt.plusSeconds(31)
        );

        assertThat(matchingTelemetry.beginSearch(
                seed.trip().getId(),
                MatchingTelemetryTrigger.RECOVERY,
                null,
                1,
                startedAt.plusSeconds(32)
        )).isEmpty();
        assertThat(matchingTelemetry.findExpiredOffers(startedAt.plusSeconds(32), 10))
                .singleElement()
                .satisfies(offer -> {
                    assertThat(offer.tripId()).isEqualTo(seed.trip().getId());
                    assertThat(offer.driverId()).isEqualTo(seed.driver().getId());
                    assertThat(offer.attempt()).isEqualTo(1);
                    assertThat(offer.excludedDriverIds()).containsExactly(seed.driver().getId());
                });
    }

    @Test
    void telemetryAdapterCancelsOpenOfferAndRunIdempotently() {
        Seed seed = seed();
        Instant startedAt = Instant.parse("2026-07-28T06:30:00Z");
        matchingTelemetry.beginSearch(
                seed.trip().getId(),
                MatchingTelemetryTrigger.BOOKING_CREATED,
                null,
                1,
                startedAt
        );
        matchingTelemetry.recordOffer(
                seed.trip().getId(),
                seed.driver().getId(),
                1,
                1,
                125.0,
                startedAt.plusSeconds(1),
                startedAt.plusSeconds(31)
        );

        matchingTelemetry.cancelRun(seed.trip().getId(), startedAt.plusSeconds(10));
        matchingTelemetry.cancelRun(seed.trip().getId(), startedAt.plusSeconds(10));

        MatchingRun run = matchingRunRepository.findByTripIdAndOutcome(
                seed.trip().getId(),
                MatchingRunOutcome.CANCELLED
        ).orElseThrow();
        assertThat(run.getFinishedAt()).isEqualTo(startedAt.plusSeconds(10));
        assertThat(matchingOfferEventRepository
                .findByMatchingRunIdAndAttemptNo(run.getId(), 1)
                .orElseThrow()
                .getOutcome()).isEqualTo(MatchingOfferOutcome.CANCELLED);
    }

    @Test
    void supplySnapshotUpsertReplacesCountsForSameGlobalBucket() {
        Instant bucketStart = Instant.parse("2026-07-28T07:00:00Z");
        transactionTemplate.executeWithoutResult(status -> {
            driverSupplySnapshotRepository.upsertSnapshot(
                    bucketStart,
                    null,
                    VehicleType.MOTORBIKE.name(),
                    4,
                    3,
                    1,
                    bucketStart.plusSeconds(1)
            );
            driverSupplySnapshotRepository.upsertSnapshot(
                    bucketStart,
                    null,
                    VehicleType.MOTORBIKE.name(),
                    6,
                    4,
                    2,
                    bucketStart.plusSeconds(2)
            );
        });

        DriverSupplySnapshot snapshot = driverSupplySnapshotRepository
                .findByBucketStartAndServiceAreaIsNullAndVehicleType(
                        bucketStart,
                        VehicleType.MOTORBIKE
                )
                .orElseThrow();
        assertThat(snapshot.getOnlineDrivers()).isEqualTo(6);
        assertThat(snapshot.getAvailableDrivers()).isEqualTo(4);
        assertThat(snapshot.getBusyDrivers()).isEqualTo(2);
        assertThat(snapshot.getSampledAt()).isEqualTo(bucketStart.plusSeconds(2));
    }

    private Seed seed() {
        int sequence = SEQUENCE.incrementAndGet();
        User passenger = userRepository.save(User.create(
                "Analytics Passenger " + sequence,
                "091%07d".formatted(sequence),
                "analytics.passenger.%d@example.com".formatted(sequence),
                "hash",
                Set.of(UserRole.PASSENGER)
        ));
        User driver = userRepository.save(User.create(
                "Analytics Driver " + sequence,
                "092%07d".formatted(sequence),
                "analytics.driver.%d@example.com".formatted(sequence),
                "hash",
                Set.of(UserRole.DRIVER)
        ));
        PricingConfig pricingConfig = pricingConfigRepository.save(PricingConfig.create(
                VehicleType.MOTORBIKE,
                BigDecimal.valueOf(10000),
                BigDecimal.valueOf(4000),
                BigDecimal.valueOf(300),
                BigDecimal.valueOf(15000),
                BigDecimal.ONE,
                Instant.parse("2026-01-01T00:00:00Z").plusSeconds(sequence)
        ));
        Trip trip = tripRepository.save(Trip.create(
                passenger,
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
        ));
        return new Seed(trip, driver);
    }

    private static org.locationtech.jts.geom.Point point(double longitude, double latitude) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
    }

    private record Seed(Trip trip, User driver) {
    }
}
