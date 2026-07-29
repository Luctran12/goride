package com.example.goride.integration;

import com.example.goride.analytics.model.AnalyticsBucket;
import com.example.goride.analytics.model.AnalyticsFilter;
import com.example.goride.analytics.repository.DirectAnalyticsQueryPort;
import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.booking.domain.PricingConfig;
import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.repository.PricingConfigRepository;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.driver.domain.VehicleType;
import com.example.goride.user.domain.User;
import com.example.goride.user.domain.UserRole;
import com.example.goride.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminAnalyticsDirectQueryIntegrationTests extends PostgresRedisIntegrationTest {
    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final Instant FROM = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-07-01T01:00:00Z");

    @Autowired
    private DirectAnalyticsQueryPort queryPort;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PricingConfigRepository pricingConfigRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void directQueriesMatchHandCalculatedFixture() {
        Fixture fixture = seedFixture();
        AnalyticsFilter filter = new AnalyticsFilter(
                FROM,
                TO,
                ZoneId.of("UTC"),
                VehicleType.MOTORBIKE,
                null
        );

        var overview = queryPort.overview(filter);
        assertThat(overview.tripRequests()).isEqualTo(3);
        assertThat(overview.completedTrips()).isEqualTo(1);
        assertThat(overview.completedTripsByRequestCohort()).isEqualTo(1);
        assertThat(overview.cancelledTrips()).isEqualTo(1);
        assertThat(overview.noDriverTrips()).isEqualTo(1);
        assertThat(overview.completedPayments()).isEqualTo(2);
        assertThat(overview.completedRevenue()).isEqualByComparingTo("300");
        assertThat(overview.matchingRuns()).isEqualTo(4);
        assertThat(overview.terminalRuns()).isEqualTo(2);
        assertThat(overview.matchedRuns()).isEqualTo(1);
        assertThat(overview.averageMatchingDurationMs()).isEqualByComparingTo("17500");
        assertThat(overview.p50MatchingDurationMs()).isEqualByComparingTo("17500");
        assertThat(overview.p95MatchingDurationMs()).isEqualByComparingTo("24250");

        var demand = queryPort.demandTimeseries(filter, AnalyticsBucket.HOUR);
        assertThat(demand).singleElement().satisfies(point -> {
            assertThat(point.bucketStart()).isEqualTo(java.time.LocalDateTime.parse(
                    "2026-07-01T00:00:00"
            ));
            assertThat(point.tripRequests()).isEqualTo(3);
            assertThat(point.completedTripsByRequestCohort()).isEqualTo(1);
        });

        var supply = queryPort.supplyTimeseries(filter, AnalyticsBucket.HOUR);
        assertThat(supply).singleElement().satisfies(point -> {
            assertThat(point.averageOnlineDrivers()).isEqualByComparingTo("10");
            assertThat(point.averageAvailableDrivers()).isEqualByComparingTo("6");
            assertThat(point.averageBusyDrivers()).isEqualByComparingTo("4");
            assertThat(point.observedBuckets()).isEqualTo(12);
        });

        var performance = queryPort.matchingPerformance(filter);
        assertThat(performance.matchingRuns()).isEqualTo(4);
        assertThat(performance.terminalRuns()).isEqualTo(2);
        assertThat(performance.matchedRuns()).isEqualTo(1);
        assertThat(performance.cancelledRuns()).isEqualTo(1);
        assertThat(performance.averageSearchesPerRun()).isEqualByComparingTo("1.5");
        assertThat(performance.averageCandidatesPerRun()).isEqualByComparingTo("3.5");
        assertThat(performance.averageOffersPerRun()).isEqualByComparingTo("1.5");
        assertThat(performance.terminalOffers()).isEqualTo(3);
        assertThat(performance.acceptedOffers()).isEqualTo(1);
        assertThat(performance.rejectedOffers()).isEqualTo(1);
        assertThat(performance.timedOutOffers()).isEqualTo(1);
        assertThat(performance.averageCandidateDistanceM()).isEqualByComparingTo("200");

        var funnel = queryPort.matchingFunnel(filter);
        assertThat(funnel.runStarted()).isEqualTo(4);
        assertThat(funnel.candidateFound()).isEqualTo(3);
        assertThat(funnel.offerSent()).isEqualTo(2);
        assertThat(funnel.offerAccepted()).isEqualTo(1);
        assertThat(funnel.tripCompleted()).isEqualTo(1);

        AnalyticsFilter emptyFilter = new AnalyticsFilter(
                Instant.parse("2026-07-03T00:00:00Z"),
                Instant.parse("2026-07-03T01:00:00Z"),
                ZoneId.of("UTC"),
                VehicleType.MOTORBIKE,
                null
        );
        var empty = queryPort.overview(emptyFilter);
        assertThat(empty.tripRequests()).isZero();
        assertThat(empty.completedRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(empty.averageMatchingDurationMs()).isNull();

        assertThat(fixture.tripAtExclusiveBoundary().getId()).isNotNull();
    }

    @Test
    void analyticsApiRequiresAdminAndReturnsValidationEnvelope() throws Exception {
        int sequence = SEQUENCE.incrementAndGet();
        String adminPhone = "094%07d".formatted(sequence);
        String passengerPhone = "095%07d".formatted(sequence);
        userRepository.save(User.create(
                "Analytics API Admin",
                adminPhone,
                "analytics.api.admin.%d@example.com".formatted(sequence),
                passwordEncoder.encode("password123"),
                Set.of(UserRole.ADMIN)
        ));
        userRepository.save(User.create(
                "Analytics API Passenger",
                passengerPhone,
                "analytics.api.passenger.%d@example.com".formatted(sequence),
                passwordEncoder.encode("password123"),
                Set.of(UserRole.PASSENGER)
        ));
        String adminToken = login(adminPhone);
        String passengerToken = login(passengerPhone);

        mockMvc.perform(get("/api/v1/admin/analytics/overview")
                        .header("Authorization", "Bearer " + passengerToken)
                        .param("from", "2026-07-01T00:00:00Z")
                        .param("to", "2026-07-02T00:00:00Z"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/admin/analytics/overview")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("from", "2026-07-01T00:00:00Z")
                        .param("to", "2026-07-02T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sourceVariant").value("DIRECT"))
                .andExpect(jsonPath("$.data.reportingTimezone").value("Asia/Ho_Chi_Minh"))
                .andExpect(jsonPath("$.data.tripRequests").value(0))
                .andExpect(jsonPath("$.data.completionRate").doesNotExist());

        mockMvc.perform(get("/api/v1/admin/analytics/demand/timeseries")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("from", "2026-07-01T00:00:00Z")
                        .param("to", "2026-07-02T00:00:00Z")
                        .param("bucket", "MONTH"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.bucket").exists());

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/admin/analytics/overview'].get.summary"
                ).value("Get analytics overview"))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/admin/analytics/matching/funnel'].get"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/admin/analytics/demand/heatmap'].get.summary"
                ).value("Get spatial demand heatmap"));
    }

    private Fixture seedFixture() {
        int sequence = SEQUENCE.incrementAndGet();
        User passenger = userRepository.save(User.create(
                "Direct Analytics Passenger " + sequence,
                "093%07d".formatted(sequence),
                "direct.analytics.%d@example.com".formatted(sequence),
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

        Trip completedInCohort = createTrip(passenger, pricing);
        Trip cancelled = createTrip(passenger, pricing);
        Trip noDriver = createTrip(passenger, pricing);
        Trip completedFromOlderRequest = createTrip(passenger, pricing);
        Trip tripAtExclusiveBoundary = createTrip(passenger, pricing);

        updateTrip(
                completedInCohort.getId(),
                "COMPLETED",
                instant("2026-07-01T00:05:00Z"),
                instant("2026-07-01T00:50:00Z"),
                null
        );
        updateTrip(
                cancelled.getId(),
                "CANCELLED",
                instant("2026-07-01T00:10:00Z"),
                null,
                instant("2026-07-01T00:20:00Z")
        );
        updateTrip(
                noDriver.getId(),
                "NO_DRIVER",
                instant("2026-07-01T00:15:00Z"),
                null,
                null
        );
        updateTrip(
                completedFromOlderRequest.getId(),
                "COMPLETED",
                instant("2026-06-30T23:55:00Z"),
                instant("2026-07-01T00:30:00Z"),
                null
        );
        jdbcTemplate.update(
                "UPDATE trips SET deleted_at = ? WHERE id = ?",
                timestamp("2026-07-02T00:00:00Z"),
                completedFromOlderRequest.getId()
        );
        updateTrip(
                tripAtExclusiveBoundary.getId(),
                "SEARCHING",
                TO,
                null,
                null
        );
        jdbcTemplate.update(
                """
                        INSERT INTO trip_status_history
                            (trip_id, from_status, to_status, changed_at)
                        VALUES (?, 'SEARCHING', 'NO_DRIVER', ?)
                        """,
                noDriver.getId(),
                timestamp("2026-07-01T00:25:00Z")
        );

        insertPayment(completedInCohort.getId(), "COMPLETED", "100", "2026-07-01T00:55:00Z");
        insertPayment(completedFromOlderRequest.getId(), "COMPLETED", "200", "2026-07-01T00:40:00Z");
        insertPayment(cancelled.getId(), "PENDING", "999", null);

        long matchedRun = insertRun(
                completedInCohort.getId(),
                "2026-07-01T00:00:00Z",
                "2026-07-01T00:00:10Z",
                "MATCHED",
                1,
                3,
                1,
                passenger.getId(),
                null
        );
        long cancelledRun = insertRun(
                cancelled.getId(),
                "2026-07-01T00:05:00Z",
                "2026-07-01T00:05:25Z",
                "CANCELLED",
                2,
                4,
                2,
                null,
                null
        );
        insertRun(
                noDriver.getId(),
                "2026-07-01T00:10:00Z",
                null,
                "IN_PROGRESS",
                1,
                0,
                0,
                null,
                null
        );
        insertRun(
                tripAtExclusiveBoundary.getId(),
                "2026-07-01T00:55:00Z",
                "2026-07-01T01:00:00Z",
                "FAILED",
                1,
                1,
                0,
                null,
                "QUERY_TEST"
        );

        insertOffer(
                matchedRun,
                passenger.getId(),
                1,
                "2026-07-01T00:00:02Z",
                "2026-07-01T00:00:32Z",
                "2026-07-01T00:00:05Z",
                "ACCEPTED",
                100
        );
        insertOffer(
                cancelledRun,
                passenger.getId(),
                1,
                "2026-07-01T00:05:02Z",
                "2026-07-01T00:05:32Z",
                "2026-07-01T00:05:05Z",
                "REJECTED",
                200
        );
        insertOffer(
                cancelledRun,
                passenger.getId(),
                2,
                "2026-07-01T00:05:07Z",
                "2026-07-01T00:05:37Z",
                null,
                "TIMEOUT",
                300
        );

        for (int minute = 0; minute < 60; minute += 5) {
            Instant bucketStart = FROM.plusSeconds(minute * 60L);
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
                    Timestamp.from(bucketStart),
                    Timestamp.from(bucketStart.plusSeconds(1))
            );
        }
        return new Fixture(tripAtExclusiveBoundary);
    }

    private String login(String phone) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "phone": "%s",
                                          "password": "password123"
                                        }
                                        """.formatted(phone)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .at("/data/accessToken")
                .asText();
    }

    private Trip createTrip(User passenger, PricingConfig pricing) {
        return tripRepository.saveAndFlush(Trip.create(
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
                pricing
        ));
    }

    private void updateTrip(
            long tripId,
            String status,
            Instant requestedAt,
            Instant completedAt,
            Instant cancelledAt
    ) {
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
                completedAt == null ? null : Timestamp.from(completedAt),
                cancelledAt == null ? null : Timestamp.from(cancelledAt),
                tripId
        );
    }

    private void insertPayment(long tripId, String status, String amount, String paidAt) {
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
                paidAt == null ? null : timestamp(paidAt),
                Timestamp.from(FROM),
                Timestamp.from(FROM)
        );
    }

    private long insertRun(
            long tripId,
            String startedAt,
            String finishedAt,
            String outcome,
            int searchCount,
            int candidateCount,
            int offerCount,
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
                        VALUES (?, ?, ?, ?, ?, 'BOOKING_CREATED', ?, ?, ?, ?, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                tripId,
                timestamp(startedAt),
                finishedAt == null ? null : timestamp(finishedAt),
                outcome,
                matchedDriverId,
                searchCount,
                candidateCount,
                offerCount,
                failureReason,
                Timestamp.from(FROM),
                Timestamp.from(FROM)
        );
    }

    private void insertOffer(
            long runId,
            long driverId,
            int attempt,
            String offeredAt,
            String expiresAt,
            String respondedAt,
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
                timestamp(offeredAt),
                timestamp(expiresAt),
                respondedAt == null ? null : timestamp(respondedAt),
                outcome,
                Timestamp.from(FROM),
                Timestamp.from(FROM)
        );
    }

    private static org.locationtech.jts.geom.Point point(double longitude, double latitude) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
    }

    private static Timestamp timestamp(String value) {
        return Timestamp.from(instant(value));
    }

    private static Instant instant(String value) {
        return Instant.parse(value);
    }

    private record Fixture(Trip tripAtExclusiveBoundary) {
    }
}
