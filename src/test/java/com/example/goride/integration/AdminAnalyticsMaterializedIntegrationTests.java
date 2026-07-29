package com.example.goride.integration;

import com.example.goride.analytics.model.AnalyticsBucket;
import com.example.goride.analytics.model.AnalyticsFilter;
import com.example.goride.analytics.model.AnalyticsSourceVariant;
import com.example.goride.analytics.repository.DirectAnalyticsQueryPort;
import com.example.goride.analytics.repository.MaterializedAnalyticsQueryPort;
import com.example.goride.analytics.service.AdminAnalyticsQueryService;
import com.example.goride.analytics.service.MaterializedAnalyticsRefreshService;
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
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminAnalyticsMaterializedIntegrationTests extends PostgresRedisIntegrationTest {
    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final Instant FROM = Instant.parse("2026-06-30T17:00:00Z");
    private static final Instant TO = Instant.parse("2026-07-01T17:00:00Z");
    private Long serviceAreaId;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MaterializedAnalyticsRefreshService refreshService;

    @Autowired
    private MaterializedAnalyticsQueryPort materializedQueryPort;

    @Autowired
    private List<DirectAnalyticsQueryPort> queryPorts;

    @Autowired
    private AdminAnalyticsQueryService analyticsService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PricingConfigRepository pricingConfigRepository;

    @Autowired
    private TripRepository tripRepository;

    @DynamicPropertySource
    static void materializedProperties(DynamicPropertyRegistry registry) {
        registry.add("app.analytics.materialized.enabled", () -> "true");
        registry.add("app.analytics.materialized.query-variant", () -> "MATERIALIZED");
        registry.add("app.analytics.materialized.refresh-enabled", () -> "false");
    }

    @BeforeAll
    void applyMaterializedRelease() throws IOException {
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS analytics CASCADE");
        Path release = Path.of(
                "db",
                "releases",
                "20260729-admin-analytics-materialized"
        );
        jdbcTemplate.execute(Files.readString(release.resolve("precheck.sql")));
        jdbcTemplate.execute(Files.readString(release.resolve("apply.sql")));
        jdbcTemplate.execute(Files.readString(release.resolve("verify.sql")));
    }

    @Test
    @Order(1)
    void directAndMaterializedResultsAreEquivalentAtRefreshCutoff() {
        seedFixture();
        var first = refreshService.refresh();
        assertThat(first.refreshed()).isTrue();
        assertThat(first.concurrent()).isFalse();
        assertThat(materializedQueryPort.currentSnapshot()).get()
                .extracting(MaterializedAnalyticsQueryPort.Snapshot::completedAt)
                .isEqualTo(first.cutoff());

        AnalyticsFilter filter = filter();
        DirectAnalyticsQueryPort direct = directQueryPort();
        assertAllQueriesEquivalent(direct, filter);
        assertAllQueriesEquivalent(direct, filter(serviceAreaId));

        var response = analyticsService.getOverview(
                OffsetDateTime.parse("2026-07-01T00:00:00+07:00"),
                OffsetDateTime.parse("2026-07-02T00:00:00+07:00"),
                "Asia/Ho_Chi_Minh",
                VehicleType.MOTORBIKE,
                null
        );
        assertThat(response.sourceVariant()).isEqualTo(AnalyticsSourceVariant.MATERIALIZED);
        assertThat(response.dataFreshnessAt()).isEqualTo(first.cutoff());

        insertAdditionalTrip();
        assertThat(direct.overview(filter).tripRequests())
                .isEqualTo(materializedQueryPort.overview(filter).tripRequests() + 1);
        var staleResponse = analyticsService.getOverview(
                OffsetDateTime.parse("2026-07-01T00:00:00+07:00"),
                OffsetDateTime.parse("2026-07-02T00:00:00+07:00"),
                "Asia/Ho_Chi_Minh",
                VehicleType.MOTORBIKE,
                null
        );
        assertThat(staleResponse.sourceVariant())
                .isEqualTo(AnalyticsSourceVariant.MATERIALIZED);
        assertThat(staleResponse.dataFreshnessAt()).isEqualTo(first.cutoff());

        var second = refreshService.refresh();
        assertThat(second.refreshed()).isTrue();
        assertThat(second.concurrent()).isTrue();
        assertThat(materializedQueryPort.currentSnapshot()).get()
                .extracting(MaterializedAnalyticsQueryPort.Snapshot::completedAt)
                .isEqualTo(second.cutoff());
        assertEquivalent(direct.overview(filter), materializedQueryPort.overview(filter));
    }

    @Test
    @Order(2)
    void refreshFailureIsObservableAndRouterFallsBackToDirect() {
        jdbcTemplate.execute("DROP INDEX analytics.uq_mv_trip_daily");
        try {
            assertThatThrownBy(refreshService::refresh).isInstanceOf(RuntimeException.class);
            assertThat(jdbcTemplate.queryForObject(
                    """
                            SELECT status
                            FROM analytics.materialized_refresh_state
                            WHERE id = 1
                            """,
                    String.class
            )).isEqualTo("FAILED");

            var response = analyticsService.getOverview(
                    OffsetDateTime.parse("2026-07-01T00:00:00+07:00"),
                    OffsetDateTime.parse("2026-07-02T00:00:00+07:00"),
                    "Asia/Ho_Chi_Minh",
                    VehicleType.MOTORBIKE,
                    null
            );
            assertThat(response.sourceVariant()).isEqualTo(AnalyticsSourceVariant.DIRECT);
        } finally {
            jdbcTemplate.execute("""
                    CREATE UNIQUE INDEX uq_mv_trip_daily
                    ON analytics.mv_trip_daily (
                        metric_day,
                        vehicle_type,
                        service_area_id
                    )
                    NULLS NOT DISTINCT
                    """);
            refreshService.refresh();
        }
    }

    @Test
    @Order(3)
    void rollbackRemovesOnlyPhaseObjectsAndPreservesUnrelatedSchemaContent()
            throws IOException {
        jdbcTemplate.execute(
                "CREATE TABLE analytics.phase5_rollback_preservation_marker (id INTEGER)"
        );
        Path rollback = Path.of(
                "db",
                "releases",
                "20260729-admin-analytics-materialized",
                "rollback.sql"
        );

        jdbcTemplate.execute(Files.readString(rollback));

        assertThat(regclass("analytics.materialized_refresh_state")).isNull();
        assertThat(regclass("analytics.mv_trip_daily")).isNull();
        assertThat(regclass("analytics.mv_demand_hourly_cell")).isNull();
        assertThat(regclass("analytics.mv_supply_hourly")).isNull();
        assertThat(regclass("analytics.mv_matching_daily")).isNull();
        assertThat(regclass("analytics.phase5_rollback_preservation_marker")).isNotNull();
        jdbcTemplate.execute("DROP SCHEMA analytics CASCADE");
    }

    private void seedFixture() {
        int sequence = SEQUENCE.incrementAndGet();
        User passenger = userRepository.save(User.create(
                "Materialized Passenger " + sequence,
                "091%07d".formatted(sequence),
                "materialized.%d@example.com".formatted(sequence),
                "hash",
                Set.of(UserRole.PASSENGER)
        ));
        PricingConfig pricing = pricingConfigRepository.save(PricingConfig.create(
                VehicleType.MOTORBIKE,
                BigDecimal.valueOf(10000),
                BigDecimal.valueOf(4000),
                BigDecimal.valueOf(300),
                BigDecimal.valueOf(15000),
                BigDecimal.ONE,
                Instant.parse("2025-01-01T00:00:00Z").plusSeconds(sequence)
        ));
        serviceAreaId = jdbcTemplate.queryForObject(
                """
                        INSERT INTO service_areas
                            (
                                name,
                                city_name,
                                country_code,
                                boundary,
                                is_active,
                                created_at,
                                updated_at
                            )
                        VALUES (
                            ?,
                            'Ho Chi Minh City',
                            'VN',
                            ST_Transform(
                                ST_MakeEnvelope(
                                    684000,
                                    1189000,
                                    687000,
                                    1192000,
                                    32648
                                ),
                                4326
                            ),
                            TRUE,
                            NOW(),
                            NOW()
                        )
                        RETURNING id
                        """,
                Long.class,
                "Materialized Fixture " + sequence
        );
        Trip completed = createTrip(passenger, pricing, projectedPoint(685100, 1190100));
        Trip cancelled = createTrip(passenger, pricing, projectedPoint(685900, 1190900));
        Trip noDriver = createTrip(passenger, pricing, projectedPoint(686100, 1190500));
        updateTrip(completed.getId(), "COMPLETED", FROM.plusSeconds(600));
        updateTrip(cancelled.getId(), "CANCELLED", FROM.plusSeconds(1200));
        updateTrip(noDriver.getId(), "NO_DRIVER", FROM.plusSeconds(1800));

        jdbcTemplate.update(
                """
                        INSERT INTO trip_status_history
                            (trip_id, from_status, to_status, changed_at)
                        VALUES (?, 'SEARCHING', 'NO_DRIVER', ?)
                        """,
                noDriver.getId(),
                Timestamp.from(FROM.plusSeconds(2100))
        );
        insertPayment(completed.getId(), "COMPLETED", "100", FROM.plusSeconds(3000));
        insertPayment(cancelled.getId(), "PENDING", "999", null);

        long matchedRun = insertRun(
                completed.getId(),
                FROM.plusSeconds(100),
                FROM.plusSeconds(110),
                "MATCHED",
                1,
                3,
                passenger.getId(),
                null
        );
        long cancelledRun = insertRun(
                cancelled.getId(),
                FROM.plusSeconds(400),
                FROM.plusSeconds(425),
                "CANCELLED",
                2,
                4,
                null,
                null
        );
        insertRun(
                noDriver.getId(),
                FROM.plusSeconds(700),
                null,
                "IN_PROGRESS",
                1,
                0,
                null,
                null
        );
        insertOffer(
                matchedRun,
                passenger.getId(),
                1,
                FROM.plusSeconds(102),
                FROM.plusSeconds(132),
                FROM.plusSeconds(105),
                "ACCEPTED",
                100
        );
        insertOffer(
                cancelledRun,
                passenger.getId(),
                1,
                FROM.plusSeconds(402),
                FROM.plusSeconds(432),
                FROM.plusSeconds(405),
                "REJECTED",
                200
        );
        insertOffer(
                cancelledRun,
                passenger.getId(),
                2,
                FROM.plusSeconds(407),
                FROM.plusSeconds(437),
                null,
                "TIMEOUT",
                300
        );
        for (int minute = 0; minute < 60; minute += 5) {
            Instant bucket = FROM.plusSeconds(minute * 60L);
            jdbcTemplate.update(
                    """
                            INSERT INTO driver_supply_snapshots
                                (
                                    bucket_start,
                                    service_area_id,
                                    vehicle_type,
                                    online_drivers,
                                    available_drivers,
                                    busy_drivers,
                                    sampled_at
                                )
                            VALUES (?, NULL, 'MOTORBIKE', 10, 6, 4, ?)
                            """,
                    Timestamp.from(bucket),
                    Timestamp.from(bucket.plusSeconds(1))
            );
            jdbcTemplate.update(
                    """
                            INSERT INTO driver_supply_snapshots
                                (
                                    bucket_start,
                                    service_area_id,
                                    vehicle_type,
                                    online_drivers,
                                    available_drivers,
                                    busy_drivers,
                                    sampled_at
                                )
                            VALUES (?, ?, 'MOTORBIKE', 5, 3, 2, ?)
                            """,
                    Timestamp.from(bucket),
                    serviceAreaId,
                    Timestamp.from(bucket.plusSeconds(1))
            );
        }
    }

    private void insertAdditionalTrip() {
        int sequence = SEQUENCE.incrementAndGet();
        User passenger = userRepository.save(User.create(
                "Post Refresh Passenger " + sequence,
                "090%07d".formatted(sequence),
                "post.refresh.%d@example.com".formatted(sequence),
                "hash",
                Set.of(UserRole.PASSENGER)
        ));
        PricingConfig pricing = pricingConfigRepository.save(PricingConfig.create(
                VehicleType.MOTORBIKE,
                BigDecimal.valueOf(11000),
                BigDecimal.valueOf(4100),
                BigDecimal.valueOf(310),
                BigDecimal.valueOf(16000),
                BigDecimal.ONE,
                Instant.parse("2025-02-01T00:00:00Z").plusSeconds(sequence)
        ));
        Trip trip = createTrip(passenger, pricing, projectedPoint(685300, 1190300));
        updateTrip(trip.getId(), "SEARCHING", FROM.plusSeconds(3600));
    }

    private Trip createTrip(User passenger, PricingConfig pricing, Point pickup) {
        return tripRepository.saveAndFlush(Trip.create(
                passenger,
                VehicleType.MOTORBIKE,
                PaymentMethod.CASH,
                "Materialized pickup",
                pickup,
                "Materialized dropoff",
                projectedPoint(685500, 1191500),
                BigDecimal.valueOf(4.2),
                18,
                BigDecimal.valueOf(32000),
                pricing
        ));
    }

    private void updateTrip(long tripId, String status, Instant requestedAt) {
        Timestamp completedAt = "COMPLETED".equals(status)
                ? Timestamp.from(requestedAt.plusSeconds(300))
                : null;
        Timestamp cancelledAt = "CANCELLED".equals(status)
                ? Timestamp.from(requestedAt.plusSeconds(300))
                : null;
        jdbcTemplate.update(
                """
                        UPDATE trips
                        SET status = ?,
                            requested_at = ?,
                            completed_at = ?,
                            cancelled_at = ?
                        WHERE id = ?
                        """,
                status,
                Timestamp.from(requestedAt),
                completedAt,
                cancelledAt,
                tripId
        );
    }

    private void insertPayment(
            long tripId,
            String status,
            String amount,
            Instant paidAt
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO payments
                            (
                                trip_id,
                                amount,
                                method,
                                status,
                                paid_at,
                                created_at,
                                updated_at
                            )
                        VALUES (?, ?, 'CASH', ?, ?, ?, ?)
                        """,
                tripId,
                new BigDecimal(amount),
                status,
                paidAt == null ? null : Timestamp.from(paidAt),
                Timestamp.from(FROM),
                Timestamp.from(FROM)
        );
    }

    private long insertRun(
            long tripId,
            Instant startedAt,
            Instant finishedAt,
            String outcome,
            int searchCount,
            int candidateCount,
            Long matchedDriverId,
            String failureReason
    ) {
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO matching_runs
                            (
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
                        VALUES (?, ?, ?, ?, ?, 'BOOKING_CREATED', ?, ?, 0, ?, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                tripId,
                Timestamp.from(startedAt),
                finishedAt == null ? null : Timestamp.from(finishedAt),
                outcome,
                matchedDriverId,
                searchCount,
                candidateCount,
                failureReason,
                Timestamp.from(FROM),
                Timestamp.from(FROM)
        );
    }

    private void insertOffer(
            long runId,
            long driverId,
            int attempt,
            Instant offeredAt,
            Instant expiresAt,
            Instant respondedAt,
            String outcome,
            double distance
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO matching_offer_events
                            (
                                matching_run_id,
                                driver_id,
                                attempt_no,
                                candidate_rank,
                                candidate_distance_m,
                                offered_at,
                                expires_at,
                                responded_at,
                                outcome,
                                created_at,
                                updated_at
                            )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                runId,
                driverId,
                attempt,
                attempt,
                distance,
                Timestamp.from(offeredAt),
                Timestamp.from(expiresAt),
                respondedAt == null ? null : Timestamp.from(respondedAt),
                outcome,
                Timestamp.from(FROM),
                Timestamp.from(FROM)
        );
    }

    private Point projectedPoint(double easting, double northing) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT ST_X(point), ST_Y(point)
                        FROM (
                            SELECT ST_Transform(
                                ST_SetSRID(ST_MakePoint(?, ?), 32648),
                                4326
                            ) AS point
                        ) transformed
                        """,
                (resultSet, rowNumber) -> GEOMETRY_FACTORY.createPoint(new Coordinate(
                        resultSet.getDouble(1),
                        resultSet.getDouble(2)
                )),
                easting,
                northing
        );
    }

    private DirectAnalyticsQueryPort directQueryPort() {
        return queryPorts.stream()
                .filter(port -> !(port instanceof MaterializedAnalyticsQueryPort))
                .findFirst()
                .orElseThrow();
    }

    private AnalyticsFilter filter() {
        return filter(null);
    }

    private AnalyticsFilter filter(Long selectedServiceAreaId) {
        return new AnalyticsFilter(
                FROM,
                TO,
                ZoneId.of("Asia/Ho_Chi_Minh"),
                VehicleType.MOTORBIKE,
                selectedServiceAreaId
        );
    }

    private void assertAllQueriesEquivalent(
            DirectAnalyticsQueryPort direct,
            AnalyticsFilter selectedFilter
    ) {
        assertEquivalent(
                direct.overview(selectedFilter),
                materializedQueryPort.overview(selectedFilter)
        );
        for (AnalyticsBucket bucket : List.of(AnalyticsBucket.HOUR, AnalyticsBucket.DAY)) {
            assertEquivalent(
                    direct.demandTimeseries(selectedFilter, bucket),
                    materializedQueryPort.demandTimeseries(selectedFilter, bucket)
            );
            assertEquivalent(
                    direct.supplyTimeseries(selectedFilter, bucket),
                    materializedQueryPort.supplyTimeseries(selectedFilter, bucket)
            );
        }
        assertEquivalent(
                direct.matchingPerformance(selectedFilter),
                materializedQueryPort.matchingPerformance(selectedFilter)
        );
        assertEquivalent(
                direct.matchingFunnel(selectedFilter),
                materializedQueryPort.matchingFunnel(selectedFilter)
        );
        assertEquivalent(
                direct.demandHeatmap(selectedFilter, 1000, 32648, null, 5001),
                materializedQueryPort.demandHeatmap(
                        selectedFilter,
                        1000,
                        32648,
                        null,
                        5001
                )
        );
    }

    private String regclass(String name) {
        return jdbcTemplate.queryForObject(
                "SELECT to_regclass(?)::TEXT",
                String.class,
                name
        );
    }

    private void assertEquivalent(Object direct, Object materialized) {
        assertThat(materialized)
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(direct);
    }
}
