package com.example.goride.analytics.repository;

import com.example.goride.analytics.config.AnalyticsMaterializedProperties;
import com.example.goride.analytics.model.AnalyticsBucket;
import com.example.goride.analytics.model.AnalyticsFilter;
import com.example.goride.analytics.model.SpatialBounds;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(
        name = "app.analytics.materialized.enabled",
        havingValue = "true"
)
public class JdbcMaterializedAnalyticsQueryAdapter
        implements MaterializedAnalyticsQueryPort {
    private static final String DIMENSION_FILTER = """
            AND (:vehicleType IS NULL OR vehicle_type = :vehicleType)
            AND service_area_id IS NOT DISTINCT FROM :serviceAreaId
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AnalyticsMaterializedProperties properties;

    public JdbcMaterializedAnalyticsQueryAdapter(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            AnalyticsMaterializedProperties properties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public Optional<Snapshot> currentSnapshot() {
        String sql = """
                SELECT
                    last_completed_at,
                    reporting_timezone,
                    projected_srid,
                    base_cell_size_meters
                FROM analytics.materialized_refresh_state
                WHERE id = 1
                  AND status = 'SUCCESS'
                  AND last_completed_at IS NOT NULL
                """;
        return jdbcTemplate.query(sql, parameters(), resultSet -> {
            if (!resultSet.next()) {
                return Optional.empty();
            }
            return Optional.of(new Snapshot(
                    resultSet.getObject("last_completed_at", OffsetDateTime.class).toInstant(),
                    resultSet.getString("reporting_timezone"),
                    resultSet.getInt("projected_srid"),
                    resultSet.getInt("base_cell_size_meters")
            ));
        });
    }

    @Override
    public OverviewStats overview(AnalyticsFilter filter) {
        String sql = """
                WITH selected_matching AS (
                    SELECT *
                    FROM analytics.mv_matching_daily
                    WHERE metric_day >= :fromDay
                      AND metric_day < :toDay
                      %s
                ),
                duration_values AS (
                    SELECT duration
                    FROM selected_matching
                    CROSS JOIN LATERAL unnest(duration_samples_ms) duration
                )
                SELECT
                    COALESCE((
                        SELECT SUM(trip_requests)
                        FROM analytics.mv_trip_daily
                        WHERE metric_day >= :fromDay
                          AND metric_day < :toDay
                          %s
                    ), 0) AS trip_requests,
                    COALESCE((
                        SELECT SUM(completed_trips)
                        FROM analytics.mv_trip_daily
                        WHERE metric_day >= :fromDay
                          AND metric_day < :toDay
                          %s
                    ), 0) AS completed_trips,
                    COALESCE((
                        SELECT SUM(completed_request_cohort)
                        FROM analytics.mv_trip_daily
                        WHERE metric_day >= :fromDay
                          AND metric_day < :toDay
                          %s
                    ), 0) AS completed_request_cohort,
                    COALESCE((
                        SELECT SUM(cancelled_trips)
                        FROM analytics.mv_trip_daily
                        WHERE metric_day >= :fromDay
                          AND metric_day < :toDay
                          %s
                    ), 0) AS cancelled_trips,
                    COALESCE((
                        SELECT SUM(no_driver_trips)
                        FROM analytics.mv_trip_daily
                        WHERE metric_day >= :fromDay
                          AND metric_day < :toDay
                          %s
                    ), 0) AS no_driver_trips,
                    COALESCE((
                        SELECT SUM(completed_payments)
                        FROM analytics.mv_trip_daily
                        WHERE metric_day >= :fromDay
                          AND metric_day < :toDay
                          %s
                    ), 0) AS completed_payments,
                    COALESCE((
                        SELECT SUM(completed_revenue)
                        FROM analytics.mv_trip_daily
                        WHERE metric_day >= :fromDay
                          AND metric_day < :toDay
                          %s
                    ), 0) AS completed_revenue,
                    COALESCE((SELECT SUM(matching_runs) FROM selected_matching), 0)
                        AS matching_runs,
                    COALESCE((SELECT SUM(terminal_runs) FROM selected_matching), 0)
                        AS terminal_runs,
                    COALESCE((SELECT SUM(matched_runs) FROM selected_matching), 0)
                        AS matched_runs,
                    (SELECT AVG(duration) FROM duration_values) AS avg_duration_ms,
                    (
                        SELECT percentile_cont(0.50) WITHIN GROUP (ORDER BY duration)
                        FROM duration_values
                    ) AS p50_duration_ms,
                    (
                        SELECT percentile_cont(0.95) WITHIN GROUP (ORDER BY duration)
                        FROM duration_values
                    ) AS p95_duration_ms
                """.formatted(
                DIMENSION_FILTER,
                DIMENSION_FILTER,
                DIMENSION_FILTER,
                DIMENSION_FILTER,
                DIMENSION_FILTER,
                DIMENSION_FILTER,
                DIMENSION_FILTER,
                DIMENSION_FILTER
        );
        return jdbcTemplate.query(sql, parameters(filter), singleRow(resultSet ->
                new OverviewStats(
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
                )
        ));
    }

    @Override
    public List<DemandBucketStats> demandTimeseries(
            AnalyticsFilter filter,
            AnalyticsBucket bucket
    ) {
        String sql = """
                SELECT
                    date_trunc(:bucketUnit, timezone(:timezone, bucket_start))
                        AS bucket_start,
                    SUM(trip_requests)::BIGINT AS trip_requests,
                    SUM(completed_request_cohort)::BIGINT
                        AS completed_request_cohort
                FROM analytics.mv_demand_hourly_cell
                WHERE bucket_start >= :from
                  AND bucket_start < :to
                  %s
                GROUP BY 1
                ORDER BY 1
                """.formatted(DIMENSION_FILTER);
        MapSqlParameterSource parameters = parameters(filter)
                .addValue("bucketUnit", bucket.sqlUnit(), Types.VARCHAR)
                .addValue("timezone", filter.reportingTimezone().getId(), Types.VARCHAR);
        return jdbcTemplate.query(sql, parameters, (resultSet, rowNumber) ->
                new DemandBucketStats(
                        resultSet.getTimestamp("bucket_start").toLocalDateTime(),
                        resultSet.getLong("trip_requests"),
                        resultSet.getLong("completed_request_cohort")
                )
        );
    }

    @Override
    public List<SupplyBucketStats> supplyTimeseries(
            AnalyticsFilter filter,
            AnalyticsBucket bucket
    ) {
        String sql = """
                WITH selected AS (
                    SELECT *
                    FROM analytics.mv_supply_hourly
                    WHERE bucket_start >= :from
                      AND bucket_start < :to
                      %s
                ),
                combined_hour AS (
                    SELECT
                        bucket_start,
                        SUM(online_driver_sum) AS online_driver_sum,
                        SUM(available_driver_sum) AS available_driver_sum,
                        SUM(busy_driver_sum) AS busy_driver_sum,
                        MAX(observed_buckets) AS observed_buckets
                    FROM selected
                    GROUP BY bucket_start
                )
                SELECT
                    date_trunc(:bucketUnit, timezone(:timezone, bucket_start))
                        AS bucket_start,
                    SUM(online_driver_sum)::NUMERIC
                        / NULLIF(SUM(observed_buckets), 0) AS avg_online_drivers,
                    SUM(available_driver_sum)::NUMERIC
                        / NULLIF(SUM(observed_buckets), 0) AS avg_available_drivers,
                    SUM(busy_driver_sum)::NUMERIC
                        / NULLIF(SUM(observed_buckets), 0) AS avg_busy_drivers,
                    SUM(observed_buckets)::BIGINT AS observed_buckets
                FROM combined_hour
                GROUP BY 1
                ORDER BY 1
                """.formatted(DIMENSION_FILTER);
        MapSqlParameterSource parameters = parameters(filter)
                .addValue("bucketUnit", bucket.sqlUnit(), Types.VARCHAR)
                .addValue("timezone", filter.reportingTimezone().getId(), Types.VARCHAR);
        return jdbcTemplate.query(sql, parameters, (resultSet, rowNumber) ->
                new SupplyBucketStats(
                        resultSet.getTimestamp("bucket_start").toLocalDateTime(),
                        decimal(resultSet, "avg_online_drivers"),
                        decimal(resultSet, "avg_available_drivers"),
                        decimal(resultSet, "avg_busy_drivers"),
                        resultSet.getLong("observed_buckets")
                )
        );
    }

    @Override
    public List<SpatialCellStats> demandHeatmap(
            AnalyticsFilter filter,
            int cellSizeMeters,
            int projectedSrid,
            SpatialBounds bounds,
            int resultLimit
    ) {
        if (bounds != null) {
            throw new IllegalArgumentException(
                    "Materialized heatmap does not support partial-cell bounding boxes"
            );
        }
        int baseCellSize = properties.getBaseCellSizeMeters();
        if (cellSizeMeters % baseCellSize != 0) {
            throw new IllegalArgumentException(
                    "Heatmap cell size must be divisible by the materialized base cell"
            );
        }
        int factor = cellSizeMeters / baseCellSize;
        String sql = """
                WITH target_cells AS (
                    SELECT
                        FLOOR(grid_x::NUMERIC / :factor)::BIGINT AS target_grid_x,
                        FLOOR(grid_y::NUMERIC / :factor)::BIGINT AS target_grid_y,
                        SUM(trip_requests)::BIGINT AS trip_requests,
                        SUM(completed_request_cohort)::BIGINT
                            AS completed_request_cohort
                    FROM analytics.mv_demand_hourly_cell
                    WHERE bucket_start >= :from
                      AND bucket_start < :to
                      %s
                    GROUP BY 1, 2
                )
                SELECT
                    CONCAT(
                        :projectedSrid,
                        ':',
                        :cellSizeMeters,
                        ':',
                        target_grid_x,
                        ':',
                        target_grid_y
                    ) AS cell_id,
                    ST_AsGeoJSON(
                        ST_Transform(
                            ST_MakeEnvelope(
                                target_grid_x * :cellSizeMeters,
                                target_grid_y * :cellSizeMeters,
                                (target_grid_x + 1) * :cellSizeMeters,
                                (target_grid_y + 1) * :cellSizeMeters,
                                :projectedSrid
                            ),
                            4326
                        ),
                        9
                    ) AS geometry_json,
                    trip_requests,
                    completed_request_cohort
                FROM target_cells
                ORDER BY trip_requests DESC, cell_id
                LIMIT :resultLimit
                """.formatted(DIMENSION_FILTER);
        MapSqlParameterSource parameters = parameters(filter)
                .addValue("factor", factor, Types.INTEGER)
                .addValue("cellSizeMeters", cellSizeMeters, Types.INTEGER)
                .addValue("projectedSrid", projectedSrid, Types.INTEGER)
                .addValue("resultLimit", resultLimit, Types.INTEGER);
        return jdbcTemplate.query(sql, parameters, (resultSet, rowNumber) ->
                new SpatialCellStats(
                        resultSet.getString("cell_id"),
                        exteriorRing(resultSet.getString("geometry_json")),
                        resultSet.getLong("trip_requests"),
                        resultSet.getLong("completed_request_cohort")
                )
        );
    }

    @Override
    public MatchingPerformanceStats matchingPerformance(AnalyticsFilter filter) {
        String sql = """
                WITH selected AS (
                    SELECT *
                    FROM analytics.mv_matching_daily
                    WHERE metric_day >= :fromDay
                      AND metric_day < :toDay
                      %s
                ),
                duration_values AS (
                    SELECT duration
                    FROM selected
                    CROSS JOIN LATERAL unnest(duration_samples_ms) duration
                )
                SELECT
                    COALESCE(SUM(matching_runs), 0) AS matching_runs,
                    COALESCE(SUM(terminal_runs), 0) AS terminal_runs,
                    COALESCE(SUM(matched_runs), 0) AS matched_runs,
                    COALESCE(SUM(no_driver_runs), 0) AS no_driver_runs,
                    COALESCE(SUM(cancelled_runs), 0) AS cancelled_runs,
                    COALESCE(SUM(failed_runs), 0) AS failed_runs,
                    (SELECT AVG(duration) FROM duration_values) AS avg_duration_ms,
                    (
                        SELECT percentile_cont(0.50) WITHIN GROUP (ORDER BY duration)
                        FROM duration_values
                    ) AS p50_duration_ms,
                    (
                        SELECT percentile_cont(0.95) WITHIN GROUP (ORDER BY duration)
                        FROM duration_values
                    ) AS p95_duration_ms,
                    SUM(searches_sum) / NULLIF(SUM(terminal_runs), 0)
                        AS avg_searches_per_run,
                    SUM(candidates_sum) / NULLIF(SUM(terminal_runs), 0)
                        AS avg_candidates_per_run,
                    SUM(offers_sum) / NULLIF(SUM(terminal_runs), 0)
                        AS avg_offers_per_run,
                    COALESCE(SUM(terminal_offers), 0) AS terminal_offers,
                    COALESCE(SUM(accepted_offers), 0) AS accepted_offers,
                    COALESCE(SUM(rejected_offers), 0) AS rejected_offers,
                    COALESCE(SUM(timed_out_offers), 0) AS timed_out_offers,
                    SUM(candidate_distance_sum)
                        / NULLIF(SUM(candidate_distance_count), 0)
                        AS avg_candidate_distance_m
                FROM selected
                """.formatted(DIMENSION_FILTER);
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
                WITH selected AS (
                    SELECT *
                    FROM analytics.mv_matching_daily
                    WHERE metric_day >= :fromDay
                      AND metric_day < :toDay
                      %s
                ),
                completed_trips AS (
                    SELECT DISTINCT trip_id
                    FROM selected
                    CROSS JOIN LATERAL unnest(completed_trip_ids) trip_id
                )
                SELECT
                    COALESCE(SUM(matching_runs), 0) AS run_started,
                    COALESCE(SUM(funnel_candidate_found), 0) AS candidate_found,
                    COALESCE(SUM(funnel_offer_sent), 0) AS offer_sent,
                    COALESCE(SUM(funnel_offer_accepted), 0) AS offer_accepted,
                    (SELECT COUNT(*) FROM completed_trips) AS trip_completed
                FROM selected
                """.formatted(DIMENSION_FILTER);
        return jdbcTemplate.query(sql, parameters(filter), singleRow(resultSet ->
                new FunnelStats(
                        resultSet.getLong("run_started"),
                        resultSet.getLong("candidate_found"),
                        resultSet.getLong("offer_sent"),
                        resultSet.getLong("offer_accepted"),
                        resultSet.getLong("trip_completed")
                )
        ));
    }

    private MapSqlParameterSource parameters(AnalyticsFilter filter) {
        return parameters()
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
                        "fromDay",
                        LocalDate.ofInstant(filter.from(), filter.reportingTimezone()),
                        Types.DATE
                )
                .addValue(
                        "toDay",
                        LocalDate.ofInstant(filter.to(), filter.reportingTimezone()),
                        Types.DATE
                )
                .addValue(
                        "vehicleType",
                        filter.vehicleType() == null ? null : filter.vehicleType().name(),
                        Types.VARCHAR
                )
                .addValue("serviceAreaId", filter.serviceAreaId(), Types.BIGINT);
    }

    private MapSqlParameterSource parameters() {
        return new MapSqlParameterSource();
    }

    private List<List<BigDecimal>> exteriorRing(String geometryJson) {
        try {
            return objectMapper.convertValue(
                    objectMapper.readTree(geometryJson).path("coordinates").path(0),
                    new TypeReference<>() {
                    }
            );
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new DataAccessResourceFailureException(
                    "PostGIS returned invalid materialized heatmap GeoJSON",
                    exception
            );
        }
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
                throw new IllegalStateException("Materialized analytics query returned no row");
            }
            return mapper.map(resultSet);
        };
    }

    @FunctionalInterface
    private interface SqlRowMapper<T> {
        T map(ResultSet resultSet) throws SQLException;
    }
}
