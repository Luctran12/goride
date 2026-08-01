package com.example.goride.integration;

import com.example.goride.analytics.service.AdminAnalyticsQueryService;
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
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminAnalyticsSpatialIntegrationTests extends PostgresRedisIntegrationTest {
    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final Instant FROM = Instant.parse("2026-07-10T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-07-11T00:00:00Z");

    @Autowired
    private AdminAnalyticsQueryService analyticsService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PricingConfigRepository pricingConfigRepository;

    @Autowired
    private TripRepository tripRepository;

    @BeforeAll
    void applySpatialIndexRelease() throws IOException {
        Path release = Path.of(
                "db",
                "releases",
                "20260729-admin-analytics-spatial-indexes"
        );
        jdbcTemplate.execute(Files.readString(release.resolve("precheck.sql")));
        jdbcTemplate.execute(Files.readString(release.resolve("apply.sql")));
        jdbcTemplate.execute(Files.readString(release.resolve("verify.sql")));
    }

    @Test
    void heatmapReturnsStableBoundaryCellsAndValidWgs84GeoJson() {
        long serviceAreaId = seedSpatialFixture();

        var response = analyticsService.getDemandHeatmap(
                OffsetDateTime.parse("2026-07-10T00:00:00Z"),
                OffsetDateTime.parse("2026-07-11T00:00:00Z"),
                "Asia/Ho_Chi_Minh",
                VehicleType.MOTORBIKE,
                serviceAreaId,
                1000,
                new BigDecimal("106.0"),
                new BigDecimal("10.0"),
                new BigDecimal("107.0"),
                new BigDecimal("11.0")
        );

        assertThat(response.type()).isEqualTo("FeatureCollection");
        assertThat(response.metadata().from()).isEqualTo(FROM);
        assertThat(response.metadata().to()).isEqualTo(TO);
        assertThat(response.metadata().reportingTimezone())
                .isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(response.features()).hasSize(2);
        assertThat(response.features()).extracting(feature -> feature.properties().cellId())
                .containsExactly(
                        "32648:1000:685:1190",
                        "32648:1000:686:1190"
                );

        var populatedCell = response.features().get(0);
        assertThat(populatedCell.properties().tripRequests()).isEqualTo(2);
        assertThat(populatedCell.properties().completedTripsByRequestCohort()).isEqualTo(1);
        assertThat(populatedCell.properties().completionRate()).isEqualByComparingTo("0.5000");
        assertValidWgs84Polygon(populatedCell.geometry().coordinates().get(0));

        var boundaryCell = response.features().get(1);
        assertThat(boundaryCell.properties().tripRequests()).isEqualTo(1);
        assertThat(boundaryCell.properties().completedTripsByRequestCohort()).isEqualTo(1);
        assertThat(boundaryCell.properties().completionRate()).isEqualByComparingTo("1.0000");
        assertValidWgs84Polygon(boundaryCell.geometry().coordinates().get(0));
    }

    @Test
    void explainAnalyzeUsesTemporalAndSpatialIndexes() {
        jdbcTemplate.execute("ANALYZE trips");
        jdbcTemplate.execute("SET LOCAL enable_seqscan = off");

        String temporalPlan = plan("""
                EXPLAIN (ANALYZE, COSTS OFF, TIMING OFF, SUMMARY OFF)
                SELECT requested_at
                FROM trips
                WHERE deleted_at IS NULL
                  AND requested_at >= TIMESTAMPTZ '2026-07-10T00:00:00Z'
                  AND requested_at < TIMESTAMPTZ '2026-07-11T00:00:00Z'
                ORDER BY requested_at
                """);
        String spatialPlan = plan("""
                EXPLAIN (ANALYZE, COSTS OFF, TIMING OFF, SUMMARY OFF)
                SELECT id
                FROM trips
                WHERE deleted_at IS NULL
                  AND pickup_location && ST_MakeEnvelope(106, 10, 107, 11, 4326)
                """);

        assertThat(temporalPlan).contains("idx_trips_analytics_requested_at");
        assertThat(spatialPlan).contains("idx_trips_pickup_location_gist");
    }

    private long seedSpatialFixture() {
        int sequence = SEQUENCE.incrementAndGet();
        User passenger = userRepository.save(User.create(
                "Spatial Analytics Passenger " + sequence,
                "092%07d".formatted(sequence),
                "spatial.analytics.%d@example.com".formatted(sequence),
                "hash",
                Set.of(UserRole.PASSENGER)
        ));
        PricingConfig motorbikePricing = pricingConfigRepository.save(PricingConfig.create(
                VehicleType.MOTORBIKE,
                BigDecimal.valueOf(10000),
                BigDecimal.valueOf(4000),
                BigDecimal.valueOf(300),
                BigDecimal.valueOf(15000),
                BigDecimal.ONE,
                Instant.parse("2026-01-01T00:00:00Z").plusSeconds(sequence * 10L)
        ));
        PricingConfig carPricing = pricingConfigRepository.save(PricingConfig.create(
                VehicleType.CAR_4_SEAT,
                BigDecimal.valueOf(20000),
                BigDecimal.valueOf(8000),
                BigDecimal.valueOf(500),
                BigDecimal.valueOf(30000),
                BigDecimal.ONE,
                Instant.parse("2026-01-01T00:00:01Z").plusSeconds(sequence * 10L)
        ));
        Long serviceAreaId = jdbcTemplate.queryForObject(
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
                                ST_MakeEnvelope(684000, 1189000, 687000, 1192000, 32648),
                                4326
                            ),
                            TRUE,
                            NOW(),
                            NOW()
                        )
                        RETURNING id
                        """,
                Long.class,
                "Spatial Fixture " + sequence
        );

        Trip completed = createTrip(
                passenger,
                motorbikePricing,
                VehicleType.MOTORBIKE,
                projectedPoint(685100, 1190100)
        );
        Trip searching = createTrip(
                passenger,
                motorbikePricing,
                VehicleType.MOTORBIKE,
                projectedPoint(685900, 1190900)
        );
        Trip boundary = createTrip(
                passenger,
                motorbikePricing,
                VehicleType.MOTORBIKE,
                projectedPoint(686000, 1190500)
        );
        Trip outsideServiceArea = createTrip(
                passenger,
                motorbikePricing,
                VehicleType.MOTORBIKE,
                projectedPoint(688000, 1190500)
        );
        Trip wrongVehicle = createTrip(
                passenger,
                carPricing,
                VehicleType.CAR_4_SEAT,
                projectedPoint(685300, 1190300)
        );
        Trip outsideTime = createTrip(
                passenger,
                motorbikePricing,
                VehicleType.MOTORBIKE,
                projectedPoint(685400, 1190400)
        );

        updateTrip(completed.getId(), "COMPLETED", FROM.plusSeconds(600));
        updateTrip(searching.getId(), "SEARCHING", FROM.plusSeconds(1200));
        updateTrip(boundary.getId(), "COMPLETED", FROM.plusSeconds(1800));
        updateTrip(outsideServiceArea.getId(), "COMPLETED", FROM.plusSeconds(2400));
        updateTrip(wrongVehicle.getId(), "COMPLETED", FROM.plusSeconds(3000));
        updateTrip(outsideTime.getId(), "COMPLETED", TO);
        return serviceAreaId;
    }

    private Trip createTrip(
            User passenger,
            PricingConfig pricing,
            VehicleType vehicleType,
            Point pickup
    ) {
        return tripRepository.saveAndFlush(Trip.create(
                passenger,
                vehicleType,
                PaymentMethod.CASH,
                "Spatial pickup",
                pickup,
                "Spatial dropoff",
                projectedPoint(685500, 1191500),
                BigDecimal.valueOf(4.2),
                18,
                BigDecimal.valueOf(32000),
                pricing
        ));
    }

    private void updateTrip(long tripId, String status, Instant requestedAt) {
        jdbcTemplate.update(
                """
                        UPDATE trips
                        SET status = ?,
                            requested_at = ?,
                            completed_at = ?
                        WHERE id = ?
                        """,
                status,
                Timestamp.from(requestedAt),
                "COMPLETED".equals(status)
                        ? Timestamp.from(requestedAt.plusSeconds(300))
                        : null,
                tripId
        );
    }

    private Point projectedPoint(double easting, double northing) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT
                            ST_X(location) AS longitude,
                            ST_Y(location) AS latitude
                        FROM (
                            SELECT ST_Transform(
                                ST_SetSRID(ST_MakePoint(?, ?), 32648),
                                4326
                            ) AS location
                        ) transformed
                        """,
                (resultSet, rowNumber) -> GEOMETRY_FACTORY.createPoint(new Coordinate(
                        resultSet.getDouble("longitude"),
                        resultSet.getDouble("latitude")
                )),
                easting,
                northing
        );
    }

    private String plan(String sql) {
        return String.join(
                System.lineSeparator(),
                jdbcTemplate.query(sql, (resultSet, rowNumber) -> resultSet.getString(1))
        );
    }

    private void assertValidWgs84Polygon(List<List<BigDecimal>> ring) {
        assertThat(ring).hasSize(5);
        assertThat(ring.get(0)).isEqualTo(ring.get(4));
        assertThat(ring).allSatisfy(coordinate -> {
            assertThat(coordinate).hasSize(2);
            assertThat(coordinate.get(0)).isBetween(
                    BigDecimal.valueOf(-180),
                    BigDecimal.valueOf(180)
            );
            assertThat(coordinate.get(1)).isBetween(
                    BigDecimal.valueOf(-90),
                    BigDecimal.valueOf(90)
            );
        });
    }
}
