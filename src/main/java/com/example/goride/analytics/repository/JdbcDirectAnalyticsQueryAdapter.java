package com.example.goride.analytics.repository;

import com.example.goride.analytics.model.AnalyticsBucket;
import com.example.goride.analytics.model.AnalyticsFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Repository
@ConditionalOnProperty(
        name = "app.analytics.direct-query-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class JdbcDirectAnalyticsQueryAdapter implements DirectAnalyticsQueryPort {
    private static final String TRIP_DIMENSION_FILTER = """
            AND (:vehicleType IS NULL OR t.vehicle_type = :vehicleType)
            AND (
                :serviceAreaId IS NULL
                OR EXISTS (
                    SELECT 1
                    FROM service_areas sa
                    WHERE sa.id = :serviceAreaId
                      AND ST_Covers(sa.boundary, t.pickup_location)
                )
            )
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcDirectAnalyticsQueryAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public OverviewStats overview(AnalyticsFilter filter) {
        String sql = """
                SELECT
                    (
                        SELECT COUNT(*)
                        FROM trips t
                        WHERE t.deleted_at IS NULL
                          AND t.requested_at >= :from
                          AND t.requested_at < :to
                          %s
                    ) AS trip_requests,
                    (
                        SELECT COUNT(*)
                        FROM trips t
                        WHERE t.deleted_at IS NULL
                          AND t.status = 'COMPLETED'
                          AND t.completed_at >= :from
                          AND t.completed_at < :to
                          %s
                    ) AS completed_trips,
                    (
                        SELECT COUNT(*)
                        FROM trips t
                        WHERE t.deleted_at IS NULL
                          AND t.status = 'COMPLETED'
                          AND t.requested_at >= :from
                          AND t.requested_at < :to
                          %s
                    ) AS completed_request_cohort,
                    (
                        SELECT COUNT(*)
                        FROM trips t
                        WHERE t.deleted_at IS NULL
                          AND t.status = 'CANCELLED'
                          AND t.cancelled_at >= :from
                          AND t.cancelled_at < :to
                          %s
                    ) AS cancelled_trips,
                    (
                        SELECT COUNT(DISTINCT h.trip_id)
                        FROM trip_status_history h
                        JOIN trips t ON t.id = h.trip_id
                        WHERE t.deleted_at IS NULL
                          AND t.status = 'NO_DRIVER'
                          AND h.to_status = 'NO_DRIVER'
                          AND h.changed_at >= :from
                          AND h.changed_at < :to
                          %s
                    ) AS no_driver_trips,
                    (
                        SELECT COUNT(*)
                        FROM payments p
                        JOIN trips t ON t.id = p.trip_id
                        WHERE p.status = 'COMPLETED'
                          AND p.paid_at >= :from
                          AND p.paid_at < :to
                          %s
                    ) AS completed_payments,
                    (
                        SELECT COALESCE(SUM(p.amount), 0)
                        FROM payments p
                        JOIN trips t ON t.id = p.trip_id
                        WHERE p.status = 'COMPLETED'
                          AND p.paid_at >= :from
                          AND p.paid_at < :to
                          %s
                    ) AS completed_revenue,
                    (
                        SELECT COUNT(*)
                        FROM matching_runs mr
                        JOIN trips t ON t.id = mr.trip_id
                        WHERE mr.started_at >= :from
                          AND mr.started_at < :to
                          %s
                    ) AS matching_runs,
                    (
                        SELECT COUNT(*)
                        FROM matching_runs mr
                        JOIN trips t ON t.id = mr.trip_id
                        WHERE mr.outcome IN ('MATCHED', 'NO_DRIVER', 'CANCELLED', 'FAILED')
                          AND mr.finished_at >= :from
                          AND mr.finished_at < :to
                          %s
                    ) AS terminal_runs,
                    (
                        SELECT COUNT(*)
                        FROM matching_runs mr
                        JOIN trips t ON t.id = mr.trip_id
                        WHERE mr.outcome = 'MATCHED'
                          AND mr.finished_at >= :from
                          AND mr.finished_at < :to
                          %s
                    ) AS matched_runs,
                    (
                        SELECT AVG(EXTRACT(EPOCH FROM (mr.finished_at - mr.started_at)) * 1000)
                        FROM matching_runs mr
                        JOIN trips t ON t.id = mr.trip_id
                        WHERE mr.outcome IN ('MATCHED', 'NO_DRIVER', 'CANCELLED', 'FAILED')
                          AND mr.finished_at >= mr.started_at
                          AND mr.finished_at >= :from
                          AND mr.finished_at < :to
                          %s
                    ) AS avg_duration_ms,
                    (
                        SELECT percentile_cont(0.50) WITHIN GROUP (
                            ORDER BY EXTRACT(EPOCH FROM (mr.finished_at - mr.started_at)) * 1000
                        )
                        FROM matching_runs mr
                        JOIN trips t ON t.id = mr.trip_id
                        WHERE mr.outcome IN ('MATCHED', 'NO_DRIVER', 'CANCELLED', 'FAILED')
                          AND mr.finished_at >= mr.started_at
                          AND mr.finished_at >= :from
                          AND mr.finished_at < :to
                          %s
                    ) AS p50_duration_ms,
                    (
                        SELECT percentile_cont(0.95) WITHIN GROUP (
                            ORDER BY EXTRACT(EPOCH FROM (mr.finished_at - mr.started_at)) * 1000
                        )
                        FROM matching_runs mr
                        JOIN trips t ON t.id = mr.trip_id
                        WHERE mr.outcome IN ('MATCHED', 'NO_DRIVER', 'CANCELLED', 'FAILED')
                          AND mr.finished_at >= mr.started_at
                          AND mr.finished_at >= :from
                          AND mr.finished_at < :to
                          %s
                    ) AS p95_duration_ms
                """.formatted(
                TRIP_DIMENSION_FILTER,
                TRIP_DIMENSION_FILTER,
                TRIP_DIMENSION_FILTER,
                TRIP_DIMENSION_FILTER,
                TRIP_DIMENSION_FILTER,
                TRIP_DIMENSION_FILTER,
                TRIP_DIMENSION_FILTER,
                TRIP_DIMENSION_FILTER,
                TRIP_DIMENSION_FILTER,
                TRIP_DIMENSION_FILTER,
                TRIP_DIMENSION_FILTER,
                TRIP_DIMENSION_FILTER,
                TRIP_DIMENSION_FILTER
        );
        return jdbcTemplate.query(sql, parameters(filter), singleRow(resultSet -> new OverviewStats(
                resultSet.getLong("trip_requests"),
                resultSet.getLong("completed_trips"),
                resultSet.getLong("completed_request_cohort"),
                resultSet.getLong("cancelled_trips"),
                resultSet.getLong("no_driver_trips"),
                resultSet.getLong("completed_payments"),
                decimal(resultSet, "completed_revenue"),
                resultSet.getLong("matching_runs"),
                resultSet.getLong("terminal_runs"),
                resultSet.getLong("matched_runs"),
                decimal(resultSet, "avg_duration_ms"),
                decimal(resultSet, "p50_duration_ms"),
                decimal(resultSet, "p95_duration_ms")
        )));
    }

    @Override
    public List<DemandBucketStats> demandTimeseries(
            AnalyticsFilter filter,
            AnalyticsBucket bucket
    ) {
        String sql = """
                SELECT
                    date_trunc(:bucketUnit, timezone(:timezone, t.requested_at)) AS bucket_start,
                    COUNT(*) AS trip_requests,
                    COUNT(*) FILTER (WHERE t.status = 'COMPLETED') AS completed_request_cohort
                FROM trips t
                WHERE t.deleted_at IS NULL
                  AND t.requested_at >= :from
                  AND t.requested_at < :to
                  %s
                GROUP BY bucket_start
                ORDER BY bucket_start
                """.formatted(TRIP_DIMENSION_FILTER);
        MapSqlParameterSource parameters = parameters(filter)
                .addValue("bucketUnit", bucket.sqlUnit(), Types.VARCHAR)
                .addValue("timezone", filter.reportingTimezone().getId(), Types.VARCHAR);
        return jdbcTemplate.query(sql, parameters, (resultSet, rowNumber) -> new DemandBucketStats(
                resultSet.getTimestamp("bucket_start").toLocalDateTime(),
                resultSet.getLong("trip_requests"),
                resultSet.getLong("completed_request_cohort")
        ));
    }

    @Override
    public List<SupplyBucketStats> supplyTimeseries(
            AnalyticsFilter filter,
            AnalyticsBucket bucket
    ) {
        String sql = """
                WITH sample_totals AS (
                    SELECT
                        s.bucket_start,
                        SUM(s.online_drivers) AS online_drivers,
                        SUM(s.available_drivers) AS available_drivers,
                        SUM(s.busy_drivers) AS busy_drivers
                    FROM driver_supply_snapshots s
                    WHERE s.bucket_start >= :from
                      AND s.bucket_start < :to
                      AND (
                          (:serviceAreaId IS NULL AND s.service_area_id IS NULL)
                          OR s.service_area_id = :serviceAreaId
                      )
                      AND (:vehicleType IS NULL OR s.vehicle_type = :vehicleType)
                    GROUP BY s.bucket_start
                )
                SELECT
                    date_trunc(:bucketUnit, timezone(:timezone, bucket_start)) AS bucket_start,
                    AVG(online_drivers) AS avg_online_drivers,
                    AVG(available_drivers) AS avg_available_drivers,
                    AVG(busy_drivers) AS avg_busy_drivers,
                    COUNT(*) AS observed_buckets
                FROM sample_totals
                GROUP BY 1
                ORDER BY 1
                """;
        MapSqlParameterSource parameters = parameters(filter)
                .addValue("bucketUnit", bucket.sqlUnit(), Types.VARCHAR)
                .addValue("timezone", filter.reportingTimezone().getId(), Types.VARCHAR);
        return jdbcTemplate.query(sql, parameters, (resultSet, rowNumber) -> new SupplyBucketStats(
                resultSet.getTimestamp("bucket_start").toLocalDateTime(),
                decimal(resultSet, "avg_online_drivers"),
                decimal(resultSet, "avg_available_drivers"),
                decimal(resultSet, "avg_busy_drivers"),
                resultSet.getLong("observed_buckets")
        ));
    }

    @Override
    public MatchingPerformanceStats matchingPerformance(AnalyticsFilter filter) {
        String sql = """
                WITH terminal_runs AS (
                    SELECT mr.*
                    FROM matching_runs mr
                    JOIN trips t ON t.id = mr.trip_id
                    WHERE mr.outcome IN ('MATCHED', 'NO_DRIVER', 'CANCELLED', 'FAILED')
                      AND mr.finished_at >= mr.started_at
                      AND mr.finished_at >= :from
                      AND mr.finished_at < :to
                      %s
                ),
                terminal_offers AS (
                    SELECT moe.*
                    FROM matching_offer_events moe
                    JOIN matching_runs mr ON mr.id = moe.matching_run_id
                    JOIN trips t ON t.id = mr.trip_id
                    WHERE moe.outcome IN ('ACCEPTED', 'REJECTED', 'TIMEOUT', 'CANCELLED', 'EXPIRED')
                      AND COALESCE(moe.responded_at, moe.expires_at) >= :from
                      AND COALESCE(moe.responded_at, moe.expires_at) < :to
                      %s
                )
                SELECT
                    (
                        SELECT COUNT(*)
                        FROM matching_runs mr
                        JOIN trips t ON t.id = mr.trip_id
                        WHERE mr.started_at >= :from
                          AND mr.started_at < :to
                          %s
                    ) AS matching_runs,
                    COUNT(*) AS terminal_runs,
                    COUNT(*) FILTER (WHERE tr.outcome = 'MATCHED') AS matched_runs,
                    COUNT(*) FILTER (WHERE tr.outcome = 'NO_DRIVER') AS no_driver_runs,
                    COUNT(*) FILTER (WHERE tr.outcome = 'CANCELLED') AS cancelled_runs,
                    COUNT(*) FILTER (WHERE tr.outcome = 'FAILED') AS failed_runs,
                    AVG(EXTRACT(EPOCH FROM (tr.finished_at - tr.started_at)) * 1000)
                        AS avg_duration_ms,
                    percentile_cont(0.50) WITHIN GROUP (
                        ORDER BY EXTRACT(EPOCH FROM (tr.finished_at - tr.started_at)) * 1000
                    ) AS p50_duration_ms,
                    percentile_cont(0.95) WITHIN GROUP (
                        ORDER BY EXTRACT(EPOCH FROM (tr.finished_at - tr.started_at)) * 1000
                    ) AS p95_duration_ms,
                    AVG(tr.search_count) AS avg_searches_per_run,
                    AVG(tr.candidate_count) AS avg_candidates_per_run,
                    (
                        SELECT COUNT(moe.id)::numeric / NULLIF(COUNT(DISTINCT run.id), 0)
                        FROM terminal_runs run
                        LEFT JOIN matching_offer_events moe ON moe.matching_run_id = run.id
                    ) AS avg_offers_per_run,
                    (SELECT COUNT(*) FROM terminal_offers) AS terminal_offers,
                    (
                        SELECT COUNT(*) FROM terminal_offers WHERE outcome = 'ACCEPTED'
                    ) AS accepted_offers,
                    (
                        SELECT COUNT(*) FROM terminal_offers WHERE outcome = 'REJECTED'
                    ) AS rejected_offers,
                    (
                        SELECT COUNT(*) FROM terminal_offers WHERE outcome = 'TIMEOUT'
                    ) AS timed_out_offers,
                    (
                        SELECT AVG(moe.candidate_distance_m)
                        FROM matching_offer_events moe
                        JOIN matching_runs mr ON mr.id = moe.matching_run_id
                        JOIN trips t ON t.id = mr.trip_id
                        WHERE moe.candidate_distance_m IS NOT NULL
                          AND moe.candidate_distance_m >= 0
                          AND moe.offered_at >= :from
                          AND moe.offered_at < :to
                          %s
                    ) AS avg_candidate_distance_m
                FROM terminal_runs tr
                """.formatted(
                TRIP_DIMENSION_FILTER,
                TRIP_DIMENSION_FILTER,
                TRIP_DIMENSION_FILTER,
                TRIP_DIMENSION_FILTER
        );
        return jdbcTemplate.query(sql, parameters(filter), singleRow(resultSet ->
                new MatchingPerformanceStats(
                        resultSet.getLong("matching_runs"),
                        resultSet.getLong("terminal_runs"),
                        resultSet.getLong("matched_runs"),
                        resultSet.getLong("no_driver_runs"),
                        resultSet.getLong("cancelled_runs"),
                        resultSet.getLong("failed_runs"),
                        decimal(resultSet, "avg_duration_ms"),
                        decimal(resultSet, "p50_duration_ms"),
                        decimal(resultSet, "p95_duration_ms"),
                        decimal(resultSet, "avg_searches_per_run"),
                        decimal(resultSet, "avg_candidates_per_run"),
                        decimal(resultSet, "avg_offers_per_run"),
                        resultSet.getLong("terminal_offers"),
                        resultSet.getLong("accepted_offers"),
                        resultSet.getLong("rejected_offers"),
                        resultSet.getLong("timed_out_offers"),
                        decimal(resultSet, "avg_candidate_distance_m")
                )
        ));
    }

    @Override
    public FunnelStats matchingFunnel(AnalyticsFilter filter) {
        String sql = """
                SELECT
                    COUNT(*) AS run_started,
                    COUNT(*) FILTER (WHERE mr.candidate_count > 0) AS candidate_found,
                    COUNT(*) FILTER (
                        WHERE EXISTS (
                            SELECT 1
                            FROM matching_offer_events sent
                            WHERE sent.matching_run_id = mr.id
                        )
                    ) AS offer_sent,
                    COUNT(*) FILTER (
                        WHERE EXISTS (
                            SELECT 1
                            FROM matching_offer_events accepted
                            WHERE accepted.matching_run_id = mr.id
                              AND accepted.outcome = 'ACCEPTED'
                        )
                    ) AS offer_accepted,
                    COUNT(DISTINCT mr.trip_id) FILTER (WHERE t.status = 'COMPLETED')
                        AS trip_completed
                FROM matching_runs mr
                JOIN trips t ON t.id = mr.trip_id
                WHERE mr.started_at >= :from
                  AND mr.started_at < :to
                  %s
                """.formatted(TRIP_DIMENSION_FILTER);
        return jdbcTemplate.query(sql, parameters(filter), singleRow(resultSet -> new FunnelStats(
                resultSet.getLong("run_started"),
                resultSet.getLong("candidate_found"),
                resultSet.getLong("offer_sent"),
                resultSet.getLong("offer_accepted"),
                resultSet.getLong("trip_completed")
        )));
    }

    private MapSqlParameterSource parameters(AnalyticsFilter filter) {
        return new MapSqlParameterSource()
                .addValue(
                        "from",
                        filter.from().atOffset(ZoneOffset.UTC),
                        Types.TIMESTAMP_WITH_TIMEZONE
                )
                .addValue(
                        "to",
                        filter.to().atOffset(ZoneOffset.UTC),
                        Types.TIMESTAMP_WITH_TIMEZONE
                )
                .addValue(
                        "vehicleType",
                        filter.vehicleType() == null ? null : filter.vehicleType().name(),
                        Types.VARCHAR
                )
                .addValue("serviceAreaId", filter.serviceAreaId(), Types.BIGINT);
    }

    private static BigDecimal decimal(ResultSet resultSet, String column) throws SQLException {
        Object value = resultSet.getObject(column);
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        return new BigDecimal(value.toString());
    }

    private static <T> ResultSetExtractor<T> singleRow(SqlRowMapper<T> mapper) {
        return resultSet -> {
            if (!resultSet.next()) {
                throw new IllegalStateException("Analytics aggregate query returned no row");
            }
            return mapper.map(resultSet);
        };
    }

    @FunctionalInterface
    private interface SqlRowMapper<T> {
        T map(ResultSet resultSet) throws SQLException;
    }
}
