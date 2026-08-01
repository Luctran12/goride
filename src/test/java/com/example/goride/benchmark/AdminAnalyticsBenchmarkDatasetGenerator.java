package com.example.goride.benchmark;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class AdminAnalyticsBenchmarkDatasetGenerator {
    private static final Path GENERATOR_PATH = Path.of(
            "benchmarks",
            "admin-analytics",
            "generate-dataset.sql"
    );
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AdminAnalyticsBenchmarkDatasetGenerator(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public DatasetManifest generate(
            AdminAnalyticsBenchmarkProfile profile,
            long seed
    ) throws IOException {
        if (seed < 0) {
            throw new IllegalArgumentException("Benchmark seed must be non-negative");
        }
        Instant startedAt = Instant.now();
        String sql = Files.readString(GENERATOR_PATH);
        String canonicalSql = sql.replace("\r\n", "\n").replace('\r', '\n');
        String generatorSqlSha256 = sha256(
                canonicalSql.getBytes(StandardCharsets.UTF_8)
        );
        Map<String, Long> replacements = replacements(profile, seed);
        for (Map.Entry<String, Long> replacement : replacements.entrySet()) {
            sql = sql.replace(
                    "${" + replacement.getKey() + "}",
                    Long.toString(replacement.getValue())
            );
        }
        if (sql.contains("${")) {
            throw new IllegalStateException("Dataset SQL contains an unresolved token");
        }
        jdbcTemplate.execute(sql);
        Instant completedAt = Instant.now();

        Map<String, Long> rowCounts = rowCounts();
        assertExpectedCounts(profile, rowCounts);
        assertStateSemantics();
        Map<String, Map<String, Long>> distributions = distributions();
        assertExpectedDistributions(distributions);
        Map<String, Object> fingerprintPayload = new LinkedHashMap<>();
        fingerprintPayload.put("generatorVersion", profile.generatorVersion());
        fingerprintPayload.put("generatorSqlSha256", generatorSqlSha256);
        fingerprintPayload.put("profile", profile.name());
        fingerprintPayload.put("seed", seed);
        fingerprintPayload.put("rowCounts", rowCounts);
        fingerprintPayload.put("distributions", distributions);
        String fingerprint = sha256(fingerprintPayload);
        return new DatasetManifest(
                profile.generatorVersion(),
                generatorSqlSha256,
                profile.name(),
                seed,
                startedAt,
                completedAt,
                Duration.between(startedAt, completedAt).toMillis(),
                true,
                rowCounts,
                distributions,
                fingerprint
        );
    }

    private Map<String, Long> replacements(
            AdminAnalyticsBenchmarkProfile profile,
            long seed
    ) {
        Map<String, Long> values = new LinkedHashMap<>();
        values.put("SEED", seed);
        values.put("USERS", profile.users());
        values.put("DRIVERS", profile.drivers());
        values.put("TRIPS", profile.trips());
        values.put("MATCHING_RUNS", profile.matchingRuns());
        values.put("MATCHING_OFFERS", profile.matchingOffers());
        values.put("SUPPLY_SNAPSHOTS", profile.driverSupplySnapshots());
        values.put("LOCATION_POINTS", profile.locationPoints());
        values.put("PAYMENTS", profile.payments());
        return values;
    }

    private Map<String, Long> rowCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("users", count("users"));
        counts.put("driver_profiles", count("driver_profiles"));
        counts.put("trips", count("trips"));
        counts.put("trip_status_history", count("trip_status_history"));
        counts.put("matching_runs", count("matching_runs"));
        counts.put("matching_offer_events", count("matching_offer_events"));
        counts.put("driver_supply_snapshots", count("driver_supply_snapshots"));
        counts.put("trip_location_history", count("trip_location_history"));
        counts.put("payments", count("payments"));
        return immutableLinkedMap(counts);
    }

    private long count(String table) {
        Long value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return value == null ? 0 : value;
    }

    private void assertExpectedCounts(
            AdminAnalyticsBenchmarkProfile profile,
            Map<String, Long> counts
    ) {
        assertCount(counts, "users", profile.users());
        assertCount(counts, "driver_profiles", profile.drivers());
        assertCount(counts, "trips", profile.trips());
        assertCount(counts, "matching_runs", profile.matchingRuns());
        assertCount(counts, "matching_offer_events", profile.matchingOffers());
        assertCount(
                counts,
                "driver_supply_snapshots",
                profile.driverSupplySnapshots()
        );
        assertCount(counts, "trip_location_history", profile.locationPoints());
        assertCount(counts, "payments", profile.payments());
    }

    private void assertCount(
            Map<String, Long> counts,
            String table,
            long expected
    ) {
        long actual = counts.getOrDefault(table, -1L);
        if (actual != expected) {
            throw new IllegalStateException(
                    "Dataset row count mismatch for " + table
                            + ": expected=" + expected + ", actual=" + actual
            );
        }
    }

    private void assertExpectedDistributions(
            Map<String, Map<String, Long>> distributions
    ) {
        assertDistributionKeys(
                distributions,
                "tripsByStatus",
                "CANCELLED",
                "COMPLETED",
                "NO_DRIVER",
                "SEARCHING"
        );
        assertDistributionKeys(
                distributions,
                "matchingRunsByOutcome",
                "CANCELLED",
                "FAILED",
                "IN_PROGRESS",
                "MATCHED",
                "NO_DRIVER"
        );
        assertDistributionKeys(
                distributions,
                "tripRequestsByPeriod",
                "MORNING_PEAK",
                "EVENING_PEAK",
                "OFF_PEAK"
        );
        assertDistributionKeys(
                distributions,
                "tripRequestsBySpatialCluster",
                "HOTSPOT_CENTRAL",
                "HOTSPOT_EAST",
                "DISTRIBUTED"
        );
    }

    private void assertStateSemantics() {
        assertNoViolations(
                "trip/matching outcome alignment",
                """
                        SELECT COUNT(*)
                        FROM matching_runs run
                        JOIN trips trip ON trip.id = run.trip_id
                        WHERE NOT (
                            (trip.status = 'COMPLETED'
                                AND run.outcome = 'MATCHED'
                                AND run.matched_driver_id = trip.driver_id)
                            OR (trip.status = 'CANCELLED'
                                AND run.outcome = 'CANCELLED')
                            OR (trip.status = 'NO_DRIVER'
                                AND run.outcome = 'NO_DRIVER')
                            OR (trip.status = 'SEARCHING'
                                AND run.outcome IN ('FAILED', 'IN_PROGRESS'))
                        )
                        """
        );
        assertNoViolations(
                "accepted offer alignment",
                """
                        SELECT COUNT(*)
                        FROM (
                            SELECT
                                run.id,
                                run.outcome,
                                run.matched_driver_id,
                                COUNT(*) FILTER (
                                    WHERE offer.outcome = 'ACCEPTED'
                                ) AS accepted_count,
                                MAX(offer.driver_id) FILTER (
                                    WHERE offer.outcome = 'ACCEPTED'
                                ) AS accepted_driver_id
                            FROM matching_runs run
                            LEFT JOIN matching_offer_events offer
                                ON offer.matching_run_id = run.id
                            GROUP BY run.id
                        ) checked
                        WHERE (outcome = 'MATCHED' AND (
                                accepted_count <> 1
                                OR accepted_driver_id <> matched_driver_id
                            ))
                           OR (outcome <> 'MATCHED' AND accepted_count <> 0)
                        """
        );
        assertNoViolations(
                "completed trip timestamp ordering",
                """
                        SELECT COUNT(*)
                        FROM trips
                        WHERE status = 'COMPLETED'
                          AND NOT (
                              requested_at < accepted_at
                              AND accepted_at < arrived_at
                              AND arrived_at < started_at
                              AND started_at < completed_at
                          )
                        """
        );
        assertNoViolations(
                "completed-payment source",
                """
                        SELECT COUNT(*)
                        FROM payments payment
                        JOIN trips trip ON trip.id = payment.trip_id
                        WHERE trip.status <> 'COMPLETED'
                           OR trip.completed_at IS NULL
                        """
        );
        assertAtLeastOne(
                "reporting-timezone day boundary",
                """
                        SELECT COUNT(*)
                        FROM trips
                        WHERE requested_at AT TIME ZONE 'Asia/Ho_Chi_Minh'
                            = date_trunc(
                                'day',
                                requested_at AT TIME ZONE 'Asia/Ho_Chi_Minh'
                            )
                        """
        );
        assertAtLeastOne(
                "250-meter projected-cell boundary",
                """
                        SELECT COUNT(*)
                        FROM trips
                        WHERE ABS(
                            ST_X(ST_Transform(pickup_location, 32648)) / 250
                            - ROUND(
                                ST_X(ST_Transform(pickup_location, 32648)) / 250
                            )
                        ) < 0.000001
                          AND ABS(
                            ST_Y(ST_Transform(pickup_location, 32648)) / 250
                            - ROUND(
                                ST_Y(ST_Transform(pickup_location, 32648)) / 250
                            )
                        ) < 0.000001
                        """
        );
        assertAtLeastOne(
                "intentional supply gap",
                """
                        SELECT COUNT(*)
                        FROM (
                            SELECT
                                bucket_start,
                                LEAD(bucket_start) OVER (
                                    PARTITION BY vehicle_type
                                    ORDER BY bucket_start
                                ) AS next_bucket
                            FROM driver_supply_snapshots
                            WHERE service_area_id IS NULL
                        ) snapshots
                        WHERE next_bucket - bucket_start > INTERVAL '5 minutes'
                        """
        );
    }

    private void assertNoViolations(String invariant, String sql) {
        Long violations = jdbcTemplate.queryForObject(sql, Long.class);
        if (violations != null && violations > 0) {
            throw new IllegalStateException(
                    "Dataset invariant failed for " + invariant
                            + ": violations=" + violations
            );
        }
    }

    private void assertAtLeastOne(String fixture, String sql) {
        Long matches = jdbcTemplate.queryForObject(sql, Long.class);
        if (matches == null || matches == 0) {
            throw new IllegalStateException(
                    "Dataset fixture is missing for " + fixture
            );
        }
    }

    private void assertDistributionKeys(
            Map<String, Map<String, Long>> distributions,
            String distribution,
            String... expectedKeys
    ) {
        Map<String, Long> values = distributions.get(distribution);
        if (values == null || !values.keySet().containsAll(java.util.List.of(expectedKeys))) {
            throw new IllegalStateException(
                    "Dataset distribution is incomplete for " + distribution
                            + ": expected=" + java.util.List.of(expectedKeys)
                            + ", actual=" + (values == null ? "missing" : values.keySet())
            );
        }
    }

    private Map<String, Map<String, Long>> distributions() {
        Map<String, Map<String, Long>> result = new LinkedHashMap<>();
        result.put(
                "tripsByStatus",
                groupCounts("SELECT status, COUNT(*) FROM trips GROUP BY 1 ORDER BY 1")
        );
        result.put(
                "matchingRunsByOutcome",
                groupCounts("""
                        SELECT outcome, COUNT(*)
                        FROM matching_runs
                        GROUP BY 1
                        ORDER BY 1
                        """)
        );
        result.put(
                "matchingOffersByOutcome",
                groupCounts("""
                        SELECT outcome, COUNT(*)
                        FROM matching_offer_events
                        GROUP BY 1
                        ORDER BY 1
                        """)
        );
        result.put(
                "paymentsByStatus",
                groupCounts("SELECT status, COUNT(*) FROM payments GROUP BY 1 ORDER BY 1")
        );
        result.put(
                "tripRequestsByPeriod",
                groupCounts("""
                        SELECT
                            CASE
                                WHEN EXTRACT(HOUR FROM requested_at AT TIME ZONE
                                    'Asia/Ho_Chi_Minh') BETWEEN 7 AND 9
                                    THEN 'MORNING_PEAK'
                                WHEN EXTRACT(HOUR FROM requested_at AT TIME ZONE
                                    'Asia/Ho_Chi_Minh') BETWEEN 16 AND 19
                                    THEN 'EVENING_PEAK'
                                ELSE 'OFF_PEAK'
                            END,
                            COUNT(*)
                        FROM trips
                        GROUP BY 1
                        ORDER BY 1
                        """)
        );
        result.put(
                "tripRequestsBySpatialCluster",
                groupCounts("""
                        SELECT
                            CASE
                                WHEN ST_X(pickup_location) BETWEEN 106.67 AND 106.70
                                 AND ST_Y(pickup_location) BETWEEN 10.75 AND 10.79
                                    THEN 'HOTSPOT_CENTRAL'
                                WHEN ST_X(pickup_location) BETWEEN 106.71 AND 106.75
                                 AND ST_Y(pickup_location) BETWEEN 10.79 AND 10.84
                                    THEN 'HOTSPOT_EAST'
                                ELSE 'DISTRIBUTED'
                            END,
                            COUNT(*)
                        FROM trips
                        GROUP BY 1
                        ORDER BY 1
                        """)
        );
        return immutableLinkedMap(result);
    }

    private Map<String, Long> groupCounts(String sql) {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbcTemplate.query(sql, resultSet -> {
            counts.put(resultSet.getString(1), resultSet.getLong(2));
        });
        return immutableLinkedMap(counts);
    }

    private <K, V> Map<K, V> immutableLinkedMap(Map<K, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private String sha256(Object value) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(value);
            return sha256(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not fingerprint benchmark dataset", exception);
        }
    }

    private String sha256(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record DatasetManifest(
            String generatorVersion,
            String generatorSqlSha256,
            String profile,
            long seed,
            Instant startedAt,
            Instant completedAt,
            long generationDurationMs,
            boolean stateSemanticsPassed,
            Map<String, Long> rowCounts,
            Map<String, Map<String, Long>> distributions,
            String distributionFingerprint
    ) {
    }
}
