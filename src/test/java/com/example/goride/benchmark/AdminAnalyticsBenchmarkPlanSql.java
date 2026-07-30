package com.example.goride.benchmark;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public final class AdminAnalyticsBenchmarkPlanSql {
    private static final ZoneId REPORTING_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private AdminAnalyticsBenchmarkPlanSql() {
    }

    public static String forCase(
            String queryId,
            String variant,
            Instant from,
            Instant to,
            String bucket,
            Integer cellSizeMeters
    ) {
        boolean materialized = "MATERIALIZED".equals(variant);
        if (queryId.startsWith("Q01_") || queryId.startsWith("Q02_")) {
            return materialized
                    ? materializedOverview(from, to)
                    : directOverview(from, to);
        }
        if (queryId.startsWith("Q03_") || queryId.startsWith("Q04_")) {
            return materialized
                    ? materializedDemand(from, to, bucket)
                    : directDemand(from, to, bucket);
        }
        if (queryId.startsWith("Q05_")) {
            return materialized
                    ? materializedSupply(from, to, bucket)
                    : directSupply(from, to, bucket);
        }
        if (queryId.startsWith("Q06_")) {
            return materialized
                    ? materializedMatching(from, to)
                    : directMatching(from, to);
        }
        if (queryId.startsWith("Q07_")) {
            return materialized
                    ? materializedFunnel(from, to)
                    : directFunnel(from, to);
        }
        if (queryId.startsWith("Q08_")
                || queryId.startsWith("Q09_")
                || queryId.startsWith("Q10_")) {
            if (cellSizeMeters == null) {
                throw new IllegalArgumentException("Heatmap plan requires a cell size");
            }
            return materialized
                    ? materializedHeatmap(from, to, cellSizeMeters)
                    : directHeatmap(from, to, cellSizeMeters);
        }
        throw new IllegalArgumentException("Unsupported benchmark query: " + queryId);
    }

    private static String directOverview(Instant from, Instant to) {
        return """
                SELECT
                    COUNT(*) AS trip_requests,
                    COUNT(*) FILTER (WHERE status = 'COMPLETED') AS completed_request_cohort,
                    (
                        SELECT COUNT(*)
                        FROM payments
                        WHERE status = 'COMPLETED'
                          AND paid_at >= %s
                          AND paid_at < %s
                    ) AS completed_payments,
                    (
                        SELECT COUNT(*)
                        FROM matching_runs
                        WHERE started_at >= %s
                          AND started_at < %s
                    ) AS matching_runs
                FROM trips
                WHERE deleted_at IS NULL
                  AND vehicle_type = 'MOTORBIKE'
                  AND requested_at >= %s
                  AND requested_at < %s
                """.formatted(
                timestamp(from),
                timestamp(to),
                timestamp(from),
                timestamp(to),
                timestamp(from),
                timestamp(to)
        );
    }

    private static String materializedOverview(Instant from, Instant to) {
        return """
                SELECT
                    COALESCE(SUM(trip_requests), 0) AS trip_requests,
                    COALESCE(SUM(completed_request_cohort), 0)
                        AS completed_request_cohort,
                    COALESCE(SUM(completed_payments), 0) AS completed_payments,
                    COALESCE(SUM(completed_revenue), 0) AS completed_revenue
                FROM analytics.mv_trip_daily
                WHERE service_area_id IS NULL
                  AND vehicle_type = 'MOTORBIKE'
                  AND metric_day >= %s
                  AND metric_day < %s
                """.formatted(date(from), date(to));
    }

    private static String directDemand(Instant from, Instant to, String bucket) {
        return """
                SELECT
                    date_trunc('%s', timezone('Asia/Ho_Chi_Minh', requested_at)),
                    COUNT(*),
                    COUNT(*) FILTER (WHERE status = 'COMPLETED')
                FROM trips
                WHERE deleted_at IS NULL
                  AND vehicle_type = 'MOTORBIKE'
                  AND requested_at >= %s
                  AND requested_at < %s
                GROUP BY 1
                ORDER BY 1
                """.formatted(bucket, timestamp(from), timestamp(to));
    }

    private static String materializedDemand(Instant from, Instant to, String bucket) {
        return """
                SELECT
                    date_trunc('%s', timezone('Asia/Ho_Chi_Minh', bucket_start)),
                    SUM(trip_requests),
                    SUM(completed_request_cohort)
                FROM analytics.mv_demand_hourly_cell
                WHERE service_area_id IS NULL
                  AND vehicle_type = 'MOTORBIKE'
                  AND bucket_start >= %s
                  AND bucket_start < %s
                GROUP BY 1
                ORDER BY 1
                """.formatted(bucket, timestamp(from), timestamp(to));
    }

    private static String directSupply(Instant from, Instant to, String bucket) {
        return """
                SELECT
                    date_trunc('%s', timezone('Asia/Ho_Chi_Minh', bucket_start)),
                    AVG(online_drivers),
                    AVG(available_drivers),
                    AVG(busy_drivers),
                    COUNT(*)
                FROM driver_supply_snapshots
                WHERE service_area_id IS NULL
                  AND vehicle_type = 'MOTORBIKE'
                  AND bucket_start >= %s
                  AND bucket_start < %s
                GROUP BY 1
                ORDER BY 1
                """.formatted(bucket, timestamp(from), timestamp(to));
    }

    private static String materializedSupply(Instant from, Instant to, String bucket) {
        return """
                SELECT
                    date_trunc('%s', timezone('Asia/Ho_Chi_Minh', bucket_start)),
                    SUM(online_driver_sum),
                    SUM(available_driver_sum),
                    SUM(busy_driver_sum),
                    SUM(observed_buckets)
                FROM analytics.mv_supply_hourly
                WHERE service_area_id IS NULL
                  AND vehicle_type = 'MOTORBIKE'
                  AND bucket_start >= %s
                  AND bucket_start < %s
                GROUP BY 1
                ORDER BY 1
                """.formatted(bucket, timestamp(from), timestamp(to));
    }

    private static String directMatching(Instant from, Instant to) {
        return """
                SELECT
                    COUNT(*) AS terminal_runs,
                    COUNT(*) FILTER (WHERE outcome = 'MATCHED') AS matched_runs,
                    AVG(EXTRACT(EPOCH FROM (finished_at - started_at)) * 1000),
                    percentile_cont(0.50) WITHIN GROUP (
                        ORDER BY EXTRACT(EPOCH FROM (finished_at - started_at)) * 1000
                    ),
                    percentile_cont(0.95) WITHIN GROUP (
                        ORDER BY EXTRACT(EPOCH FROM (finished_at - started_at)) * 1000
                    )
                FROM matching_runs
                WHERE outcome IN ('MATCHED', 'NO_DRIVER', 'CANCELLED', 'FAILED')
                  AND finished_at >= %s
                  AND finished_at < %s
                """.formatted(timestamp(from), timestamp(to));
    }

    private static String materializedMatching(Instant from, Instant to) {
        return """
                WITH selected AS (
                    SELECT terminal_runs, matched_runs, duration_samples_ms
                    FROM analytics.mv_matching_daily
                    WHERE service_area_id IS NULL
                      AND vehicle_type = 'MOTORBIKE'
                      AND metric_day >= %s
                      AND metric_day < %s
                ),
                durations AS (
                    SELECT duration
                    FROM selected
                    CROSS JOIN LATERAL unnest(duration_samples_ms) duration
                )
                SELECT
                    COALESCE((SELECT SUM(terminal_runs) FROM selected), 0),
                    COALESCE((SELECT SUM(matched_runs) FROM selected), 0),
                    (SELECT AVG(duration) FROM durations),
                    (
                        SELECT percentile_cont(0.50)
                            WITHIN GROUP (ORDER BY duration)
                        FROM durations
                    ),
                    (
                        SELECT percentile_cont(0.95)
                            WITHIN GROUP (ORDER BY duration)
                        FROM durations
                    )
                """.formatted(date(from), date(to));
    }

    private static String directFunnel(Instant from, Instant to) {
        return """
                SELECT
                    COUNT(*) AS run_started,
                    COUNT(*) FILTER (WHERE candidate_count > 0) AS candidate_found,
                    COUNT(*) FILTER (
                        WHERE EXISTS (
                            SELECT 1
                            FROM matching_offer_events offer
                            WHERE offer.matching_run_id = matching_runs.id
                        )
                    ) AS offer_sent
                FROM matching_runs
                WHERE started_at >= %s
                  AND started_at < %s
                """.formatted(timestamp(from), timestamp(to));
    }

    private static String materializedFunnel(Instant from, Instant to) {
        return """
                SELECT
                    SUM(matching_runs),
                    SUM(funnel_candidate_found),
                    SUM(funnel_offer_sent),
                    SUM(funnel_offer_accepted)
                FROM analytics.mv_matching_daily
                WHERE service_area_id IS NULL
                  AND vehicle_type = 'MOTORBIKE'
                  AND metric_day >= %s
                  AND metric_day < %s
                """.formatted(date(from), date(to));
    }

    private static String directHeatmap(
            Instant from,
            Instant to,
            int cellSizeMeters
    ) {
        return """
                SELECT
                    FLOOR(ST_X(ST_Transform(pickup_location, 32648)) / %d)::BIGINT,
                    FLOOR(ST_Y(ST_Transform(pickup_location, 32648)) / %d)::BIGINT,
                    COUNT(*)
                FROM trips
                WHERE deleted_at IS NULL
                  AND vehicle_type = 'MOTORBIKE'
                  AND requested_at >= %s
                  AND requested_at < %s
                GROUP BY 1, 2
                ORDER BY 3 DESC
                LIMIT 5001
                """.formatted(
                cellSizeMeters,
                cellSizeMeters,
                timestamp(from),
                timestamp(to)
        );
    }

    private static String materializedHeatmap(
            Instant from,
            Instant to,
            int cellSizeMeters
    ) {
        int factor = cellSizeMeters / 250;
        return """
                SELECT
                    FLOOR(grid_x::NUMERIC / %d)::BIGINT,
                    FLOOR(grid_y::NUMERIC / %d)::BIGINT,
                    SUM(trip_requests)
                FROM analytics.mv_demand_hourly_cell
                WHERE service_area_id IS NULL
                  AND vehicle_type = 'MOTORBIKE'
                  AND bucket_start >= %s
                  AND bucket_start < %s
                GROUP BY 1, 2
                ORDER BY 3 DESC
                LIMIT 5001
                """.formatted(factor, factor, timestamp(from), timestamp(to));
    }

    private static String timestamp(Instant instant) {
        return "TIMESTAMPTZ '" + instant + "'";
    }

    private static String date(Instant instant) {
        LocalDate localDate = LocalDate.ofInstant(instant, REPORTING_ZONE);
        return "DATE '" + localDate + "'";
    }
}
