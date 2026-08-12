package com.example.goride.analytics.repository;

import com.example.goride.analytics.model.SpatialBounds;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcDemandForecastQueryAdapter implements DemandForecastQueryPort {
    private static final String PROCESSING_QUALITY_CTE = """
            quality AS (
                SELECT run_id,
                       COUNT(*) FILTER (WHERE result_status = 'PASS') AS quality_pass,
                       COUNT(*) FILTER (WHERE result_status = 'WARN') AS quality_warn,
                       COUNT(*) FILTER (WHERE result_status = 'FAIL') AS quality_fail
                FROM analytics.data_quality_results
                GROUP BY run_id
            )
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcDemandForecastQueryAdapter(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ProcessingStageRow> latestProcessingStages(String sourceProfile) {
        String sql = """
                WITH ranked AS (
                    SELECT p.*,
                           ROW_NUMBER() OVER (
                               PARTITION BY p.run_type
                               ORDER BY p.created_at DESC, p.attempt_no DESC, p.run_id
                           ) AS stage_rank
                    FROM analytics.processing_runs p
                    WHERE (CAST(:sourceProfile AS VARCHAR) IS NULL
                           OR p.source_profile = :sourceProfile)
                ),
                %s
                SELECT r.run_id, r.artifact_run_id, r.run_type, r.status,
                       r.source_profile, r.dataset_version, r.source_cutoff,
                       r.started_at, r.finished_at, r.rows_read, r.rows_written,
                       COALESCE(q.quality_pass, 0) AS quality_pass,
                       COALESCE(q.quality_warn, 0) AS quality_warn,
                       COALESCE(q.quality_fail, 0) AS quality_fail
                FROM ranked r
                LEFT JOIN quality q ON q.run_id = r.run_id
                WHERE r.stage_rank = 1
                ORDER BY r.run_type
                """.formatted(PROCESSING_QUALITY_CTE);
        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource().addValue("sourceProfile", sourceProfile, Types.VARCHAR),
                (resultSet, rowNumber) -> processingStage(resultSet)
        );
    }

    @Override
    public List<ProcessingRunRow> processingRuns(
            String runType,
            String status,
            String sourceProfile,
            int offset,
            int limit
    ) {
        String sql = """
                WITH %s
                SELECT p.run_id, p.artifact_run_id, p.run_type, p.status,
                       p.source_profile, p.dataset_version, p.source_cutoff,
                       p.code_commit, p.attempt_no, p.rows_read, p.rows_written,
                       p.created_at, p.started_at, p.finished_at,
                       p.error_code, p.error_message,
                       COALESCE(q.quality_pass, 0) AS quality_pass,
                       COALESCE(q.quality_warn, 0) AS quality_warn,
                       COALESCE(q.quality_fail, 0) AS quality_fail
                FROM analytics.processing_runs p
                LEFT JOIN quality q ON q.run_id = p.run_id
                WHERE (CAST(:runType AS VARCHAR) IS NULL OR p.run_type = :runType)
                  AND (CAST(:status AS VARCHAR) IS NULL OR p.status = :status)
                  AND (CAST(:sourceProfile AS VARCHAR) IS NULL
                       OR p.source_profile = :sourceProfile)
                ORDER BY p.created_at DESC, p.run_id
                OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
                """.formatted(PROCESSING_QUALITY_CTE);
        return jdbcTemplate.query(
                sql,
                processingFilters(runType, status, sourceProfile)
                        .addValue("offset", offset)
                        .addValue("limit", limit),
                (resultSet, rowNumber) -> processingRun(resultSet)
        );
    }

    @Override
    public long countProcessingRuns(String runType, String status, String sourceProfile) {
        Long count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM analytics.processing_runs p
                        WHERE (CAST(:runType AS VARCHAR) IS NULL OR p.run_type = :runType)
                          AND (CAST(:status AS VARCHAR) IS NULL OR p.status = :status)
                          AND (CAST(:sourceProfile AS VARCHAR) IS NULL
                               OR p.source_profile = :sourceProfile)
                        """,
                processingFilters(runType, status, sourceProfile),
                Long.class
        );
        return count == null ? 0 : count;
    }

    @Override
    public Optional<ProcessingRunSummaryRow> processingRun(UUID runId) {
        return jdbcTemplate.query(
                        """
                                SELECT run_id, run_type, status, source_profile,
                                       dataset_version, source_cutoff
                                FROM analytics.processing_runs
                                WHERE run_id = :runId
                                """,
                        new MapSqlParameterSource("runId", runId),
                        (resultSet, rowNumber) -> new ProcessingRunSummaryRow(
                                uuid(resultSet, "run_id"),
                                resultSet.getString("run_type"),
                                resultSet.getString("status"),
                                resultSet.getString("source_profile"),
                                resultSet.getString("dataset_version"),
                                instant(resultSet, "source_cutoff")
                        )
                )
                .stream()
                .findFirst();
    }

    @Override
    public List<QualityRuleRow> qualityRules(UUID runId) {
        return jdbcTemplate.query(
                """
                        SELECT rule_code, scope_key, severity, result_status,
                               records_checked, records_breached, metric_value,
                               threshold::TEXT AS threshold_json,
                               details::TEXT AS details_json, evaluated_at
                        FROM analytics.data_quality_results
                        WHERE run_id = :runId
                        ORDER BY CASE result_status
                                     WHEN 'FAIL' THEN 0 WHEN 'WARN' THEN 1 ELSE 2
                                 END,
                                 rule_code, scope_key
                        """,
                new MapSqlParameterSource("runId", runId),
                (resultSet, rowNumber) -> new QualityRuleRow(
                        resultSet.getString("rule_code"),
                        resultSet.getString("scope_key"),
                        resultSet.getString("severity"),
                        resultSet.getString("result_status"),
                        resultSet.getLong("records_checked"),
                        resultSet.getLong("records_breached"),
                        resultSet.getBigDecimal("metric_value"),
                        json(resultSet.getString("threshold_json")),
                        json(resultSet.getString("details_json")),
                        instant(resultSet, "evaluated_at")
                )
        );
    }

    @Override
    public List<ModelVersionRow> modelVersions(
            String lifecycleStatus,
            String sourceProfile,
            int offset,
            int limit
    ) {
        return jdbcTemplate.query(
                """
                        SELECT model_version_id, model_name, model_version, model_family,
                               lifecycle_status,
                               training_manifest ->> 'approvalScope' AS approval_scope,
                               source_profile, dataset_version, demand_event_semantics,
                               feature_set_version, grid_version, cell_size_meters,
                               bucket_minutes, training_cutoff_utc, artifact_sha256,
                               hyperparameters::TEXT AS hyperparameters_json,
                               (training_manifest -> 'modelCard')::TEXT AS model_card_json,
                               created_at, validated_at, approved_at, retired_at
                        FROM analytics.model_versions
                        WHERE (CAST(:lifecycleStatus AS VARCHAR) IS NULL
                               OR lifecycle_status = :lifecycleStatus)
                          AND (CAST(:sourceProfile AS VARCHAR) IS NULL
                               OR source_profile = :sourceProfile)
                        ORDER BY created_at DESC, model_name, model_version
                        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
                        """,
                modelFilters(lifecycleStatus, sourceProfile)
                        .addValue("offset", offset)
                        .addValue("limit", limit),
                (resultSet, rowNumber) -> modelVersion(resultSet)
        );
    }

    @Override
    public long countModelVersions(String lifecycleStatus, String sourceProfile) {
        Long count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM analytics.model_versions
                        WHERE (CAST(:lifecycleStatus AS VARCHAR) IS NULL
                               OR lifecycle_status = :lifecycleStatus)
                          AND (CAST(:sourceProfile AS VARCHAR) IS NULL
                               OR source_profile = :sourceProfile)
                        """,
                modelFilters(lifecycleStatus, sourceProfile),
                Long.class
        );
        return count == null ? 0 : count;
    }

    @Override
    public Optional<SelectedForecastRunRow> selectForecastRun(
            UUID forecastRunId,
            String modelVersion,
            String runPurpose,
            int cellSizeMeters
    ) {
        String sql = """
                WITH candidate AS (
                    SELECT f.forecast_run_id, f.processing_run_id,
                           f.model_version_id, m.model_version, m.model_family,
                           m.feature_set_version,
                           m.training_manifest ->> 'approvalScope' AS approval_scope,
                           f.source_profile, f.dataset_version,
                           f.demand_event_semantics, f.run_purpose, f.status,
                           f.cell_size_meters, f.generated_at_utc,
                           f.inference_cutoff_utc, f.finished_at, f.published_at
                    FROM analytics.forecast_runs f
                    JOIN analytics.model_versions m
                      ON m.model_version_id = f.model_version_id
                    WHERE f.status IN ('PUBLISHED', 'SUCCEEDED')
                      AND (CAST(:forecastRunId AS UUID) IS NULL
                           OR f.forecast_run_id = :forecastRunId)
                      AND (CAST(:modelVersion AS VARCHAR) IS NULL
                           OR m.model_version = :modelVersion)
                      AND (CAST(:runPurpose AS VARCHAR) IS NULL
                           OR f.run_purpose = :runPurpose)
                      AND f.cell_size_meters = :cellSizeMeters
                    ORDER BY CASE f.status WHEN 'PUBLISHED' THEN 0 ELSE 1 END,
                             f.inference_cutoff_utc DESC, f.created_at DESC
                    LIMIT 1
                )
                SELECT c.forecast_run_id, c.processing_run_id, c.model_version_id,
                       c.model_version, c.model_family, c.feature_set_version,
                       c.approval_scope, c.source_profile, c.dataset_version,
                       c.demand_event_semantics, c.run_purpose, c.status,
                       c.cell_size_meters, c.generated_at_utc,
                       c.inference_cutoff_utc, c.finished_at, c.published_at,
                       COALESCE(s.forecast_rows, 0) AS forecast_rows,
                       COALESCE(s.evaluated_rows, 0) AS evaluated_rows,
                       s.latest_evaluated_at,
                       COALESCE(q.quality_pass, 0) AS quality_pass,
                       COALESCE(q.quality_warn, 0) AS quality_warn,
                       COALESCE(q.quality_fail, 0) AS quality_fail
                FROM candidate c
                LEFT JOIN LATERAL (
                    SELECT COUNT(*) AS forecast_rows,
                           COUNT(*) FILTER (WHERE d.actual_demand IS NOT NULL)
                               AS evaluated_rows,
                           MAX(d.evaluated_at) AS latest_evaluated_at
                    FROM analytics.demand_forecasts d
                    WHERE d.forecast_run_id = c.forecast_run_id
                ) s ON TRUE
                LEFT JOIN LATERAL (
                    SELECT COUNT(*) FILTER (WHERE q.result_status = 'PASS')
                               AS quality_pass,
                           COUNT(*) FILTER (WHERE q.result_status = 'WARN')
                               AS quality_warn,
                           COUNT(*) FILTER (WHERE q.result_status = 'FAIL')
                               AS quality_fail
                    FROM analytics.data_quality_results q
                    WHERE q.run_id = c.processing_run_id
                ) q ON TRUE
                """;
        return jdbcTemplate.query(
                        sql,
                        forecastSelection(forecastRunId, modelVersion, runPurpose, cellSizeMeters),
                        (resultSet, rowNumber) -> selectedForecastRun(resultSet)
                )
                .stream()
                .findFirst();
    }

    @Override
    public List<ForecastPointRow> demandForecasts(
            UUID forecastRunId,
            Instant from,
            Instant to,
            int horizonMinutes,
            SpatialBounds bounds,
            int limit
    ) {
        return forecastPoints(forecastRunId, from, to, horizonMinutes, bounds, limit, false);
    }

    @Override
    public List<ForecastPointRow> forecastHotspots(
            UUID forecastRunId,
            Instant from,
            Instant to,
            int horizonMinutes,
            SpatialBounds bounds,
            int limit
    ) {
        return forecastPoints(forecastRunId, from, to, horizonMinutes, bounds, limit, true);
    }

    @Override
    public List<EvaluationMetricRow> storedEvaluationMetrics(
            String modelVersion,
            Integer horizonMinutes,
            Integer cellSizeMeters,
            int limit
    ) {
        return jdbcTemplate.query(
                """
                        SELECT e.forecast_run_id, e.model_version_id,
                               m.model_version, m.model_family,
                               m.training_manifest ->> 'approvalScope' AS approval_scope,
                               e.fold_key, e.metric_name, e.horizon_minutes,
                               e.cell_size_meters, e.slice_type, e.slice_key,
                               e.metric_value, e.sample_count, e.created_at
                        FROM analytics.forecast_evaluations e
                        JOIN analytics.model_versions m
                          ON m.model_version_id = e.model_version_id
                        WHERE (CAST(:modelVersion AS VARCHAR) IS NULL
                               OR m.model_version = :modelVersion)
                          AND (CAST(:horizonMinutes AS SMALLINT) IS NULL
                               OR e.horizon_minutes = :horizonMinutes)
                          AND (CAST(:cellSizeMeters AS INTEGER) IS NULL
                               OR e.cell_size_meters = :cellSizeMeters)
                        ORDER BY m.model_version, e.fold_key, e.horizon_minutes,
                                 e.metric_name, e.slice_type NULLS FIRST, e.slice_key NULLS FIRST
                        LIMIT :limit
                        """,
                evaluationFilters(modelVersion, horizonMinutes, cellSizeMeters)
                        .addValue("limit", limit),
                (resultSet, rowNumber) -> evaluationMetric(resultSet)
        );
    }

    @Override
    public List<EvaluationMetricRow> derivedEvaluationMetrics(
            String modelVersion,
            Integer horizonMinutes,
            Integer cellSizeMeters,
            int limit
    ) {
        return jdbcTemplate.query(
                """
                        WITH aggregates AS (
                            SELECT f.forecast_run_id, f.model_version_id,
                                   m.model_version, m.model_family,
                                   m.training_manifest ->> 'approvalScope' AS approval_scope,
                                   d.horizon_minutes, d.cell_size_meters,
                                   AVG(d.absolute_error) AS mae,
                                   SQRT(AVG(POWER(d.predicted_demand - d.actual_demand, 2))) AS rmse,
                                   SUM(d.absolute_error)
                                       / NULLIF(SUM(d.actual_demand), 0) AS wape,
                                   COUNT(*) AS sample_count,
                                   MAX(d.evaluated_at) AS evaluated_at
                            FROM analytics.forecast_runs f
                            JOIN analytics.model_versions m
                              ON m.model_version_id = f.model_version_id
                            JOIN analytics.demand_forecasts d
                              ON d.forecast_run_id = f.forecast_run_id
                            WHERE f.status IN ('PUBLISHED', 'SUCCEEDED')
                              AND d.actual_demand IS NOT NULL
                              AND (CAST(:modelVersion AS VARCHAR) IS NULL
                                   OR m.model_version = :modelVersion)
                              AND (CAST(:horizonMinutes AS SMALLINT) IS NULL
                                   OR d.horizon_minutes = :horizonMinutes)
                              AND (CAST(:cellSizeMeters AS INTEGER) IS NULL
                                   OR d.cell_size_meters = :cellSizeMeters)
                            GROUP BY f.forecast_run_id, f.model_version_id,
                                     m.model_version, m.model_family, approval_scope,
                                     d.horizon_minutes, d.cell_size_meters
                        )
                        SELECT a.forecast_run_id, a.model_version_id, a.model_version,
                               a.model_family, a.approval_scope,
                               'ACTUAL_BACKFILL' AS fold_key,
                               metric.metric_name, a.horizon_minutes, a.cell_size_meters,
                               NULL::VARCHAR AS slice_type, NULL::VARCHAR AS slice_key,
                               metric.metric_value, a.sample_count, a.evaluated_at AS created_at
                        FROM aggregates a
                        CROSS JOIN LATERAL (
                            VALUES ('MAE', a.mae), ('RMSE', a.rmse), ('WAPE', a.wape)
                        ) metric(metric_name, metric_value)
                        WHERE metric.metric_value IS NOT NULL
                        ORDER BY a.model_version, a.horizon_minutes, metric.metric_name
                        LIMIT :limit
                        """,
                evaluationFilters(modelVersion, horizonMinutes, cellSizeMeters)
                        .addValue("limit", limit),
                (resultSet, rowNumber) -> evaluationMetric(resultSet)
        );
    }

    @Override
    public List<ForecastRunHistoryRow> forecastRuns(
            String status,
            String runPurpose,
            String modelVersion,
            String sourceProfile,
            int offset,
            int limit
    ) {
        String sql = """
                WITH page AS (
                    SELECT f.forecast_run_id, f.processing_run_id,
                           f.model_version_id, m.model_version, m.model_family,
                           m.training_manifest ->> 'approvalScope' AS approval_scope,
                           f.run_purpose, f.status, f.source_profile,
                           f.dataset_version, f.demand_event_semantics,
                           f.grid_version, f.cell_size_meters, f.bucket_minutes,
                           f.inference_cutoff_utc, f.generated_at_utc,
                           f.finished_at, f.published_at, f.error_code,
                           f.error_message, f.created_at
                    FROM analytics.forecast_runs f
                    JOIN analytics.model_versions m
                      ON m.model_version_id = f.model_version_id
                    WHERE (CAST(:status AS VARCHAR) IS NULL OR f.status = :status)
                      AND (CAST(:runPurpose AS VARCHAR) IS NULL
                           OR f.run_purpose = :runPurpose)
                      AND (CAST(:modelVersion AS VARCHAR) IS NULL
                           OR m.model_version = :modelVersion)
                      AND (CAST(:sourceProfile AS VARCHAR) IS NULL
                           OR f.source_profile = :sourceProfile)
                    ORDER BY f.created_at DESC, f.forecast_run_id
                    OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
                )
                SELECT p.forecast_run_id, p.processing_run_id, p.model_version_id,
                       p.model_version, p.model_family, p.approval_scope,
                       p.run_purpose, p.status, p.source_profile, p.dataset_version,
                       p.demand_event_semantics, p.grid_version, p.cell_size_meters,
                       p.bucket_minutes, p.inference_cutoff_utc, p.generated_at_utc,
                       p.finished_at, p.published_at, p.error_code, p.error_message,
                       COALESCE(s.forecast_rows, 0) AS forecast_rows,
                       COALESCE(s.evaluated_rows, 0) AS evaluated_rows,
                       s.latest_evaluated_at,
                       COALESCE(q.quality_pass, 0) AS quality_pass,
                       COALESCE(q.quality_warn, 0) AS quality_warn,
                       COALESCE(q.quality_fail, 0) AS quality_fail
                FROM page p
                LEFT JOIN LATERAL (
                    SELECT COUNT(*) AS forecast_rows,
                           COUNT(*) FILTER (WHERE d.actual_demand IS NOT NULL)
                               AS evaluated_rows,
                           MAX(d.evaluated_at) AS latest_evaluated_at
                    FROM analytics.demand_forecasts d
                    WHERE d.forecast_run_id = p.forecast_run_id
                ) s ON TRUE
                LEFT JOIN LATERAL (
                    SELECT COUNT(*) FILTER (WHERE q.result_status = 'PASS')
                               AS quality_pass,
                           COUNT(*) FILTER (WHERE q.result_status = 'WARN')
                               AS quality_warn,
                           COUNT(*) FILTER (WHERE q.result_status = 'FAIL')
                               AS quality_fail
                    FROM analytics.data_quality_results q
                    WHERE q.run_id = p.processing_run_id
                ) q ON TRUE
                ORDER BY p.created_at DESC, p.forecast_run_id
                """;
        return jdbcTemplate.query(
                sql,
                forecastRunFilters(status, runPurpose, modelVersion, sourceProfile)
                        .addValue("offset", offset)
                        .addValue("limit", limit),
                (resultSet, rowNumber) -> forecastRunHistory(resultSet)
        );
    }

    @Override
    public long countForecastRuns(
            String status,
            String runPurpose,
            String modelVersion,
            String sourceProfile
    ) {
        Long count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM analytics.forecast_runs f
                        JOIN analytics.model_versions m ON m.model_version_id = f.model_version_id
                        WHERE (CAST(:status AS VARCHAR) IS NULL OR f.status = :status)
                          AND (CAST(:runPurpose AS VARCHAR) IS NULL
                               OR f.run_purpose = :runPurpose)
                          AND (CAST(:modelVersion AS VARCHAR) IS NULL
                               OR m.model_version = :modelVersion)
                          AND (CAST(:sourceProfile AS VARCHAR) IS NULL
                               OR f.source_profile = :sourceProfile)
                        """,
                forecastRunFilters(status, runPurpose, modelVersion, sourceProfile),
                Long.class
        );
        return count == null ? 0 : count;
    }

    private List<ForecastPointRow> forecastPoints(
            UUID forecastRunId,
            Instant from,
            Instant to,
            int horizonMinutes,
            SpatialBounds bounds,
            int limit,
            boolean hotspots
    ) {
        String ordering = hotspots
                ? "d.predicted_demand DESC, d.cell_id, d.target_bucket_start_utc"
                : "d.target_bucket_start_utc, d.cell_id";
        String sql = """
                SELECT d.cell_id, ST_AsGeoJSON(d.cell_geometry) AS geometry_json,
                       d.target_bucket_start_utc, d.horizon_minutes,
                       d.predicted_demand, d.prediction_lower, d.prediction_upper,
                       d.actual_demand, d.absolute_error, d.evaluated_at
                FROM analytics.demand_forecasts d
                WHERE d.forecast_run_id = :forecastRunId
                  AND d.target_bucket_start_utc >= :from
                  AND d.target_bucket_start_utc < :to
                  AND d.horizon_minutes = :horizonMinutes
                  AND (
                      :bounded = FALSE
                      OR d.cell_geometry && ST_MakeEnvelope(
                          :minLongitude, :minLatitude,
                          :maxLongitude, :maxLatitude, 4326
                      )
                  )
                ORDER BY %s
                LIMIT :limit
                """.formatted(ordering);
        return jdbcTemplate.query(
                sql,
                forecastPointParameters(forecastRunId, from, to, horizonMinutes, bounds, limit),
                (resultSet, rowNumber) -> forecastPoint(resultSet)
        );
    }

    private MapSqlParameterSource processingFilters(
            String runType,
            String status,
            String sourceProfile
    ) {
        return new MapSqlParameterSource()
                .addValue("runType", runType, Types.VARCHAR)
                .addValue("status", status, Types.VARCHAR)
                .addValue("sourceProfile", sourceProfile, Types.VARCHAR);
    }

    private MapSqlParameterSource modelFilters(String lifecycleStatus, String sourceProfile) {
        return new MapSqlParameterSource()
                .addValue("lifecycleStatus", lifecycleStatus, Types.VARCHAR)
                .addValue("sourceProfile", sourceProfile, Types.VARCHAR);
    }

    private MapSqlParameterSource forecastSelection(
            UUID forecastRunId,
            String modelVersion,
            String runPurpose,
            int cellSizeMeters
    ) {
        return new MapSqlParameterSource()
                .addValue("forecastRunId", forecastRunId, Types.OTHER)
                .addValue("modelVersion", modelVersion, Types.VARCHAR)
                .addValue("runPurpose", runPurpose, Types.VARCHAR)
                .addValue("cellSizeMeters", cellSizeMeters);
    }

    private MapSqlParameterSource evaluationFilters(
            String modelVersion,
            Integer horizonMinutes,
            Integer cellSizeMeters
    ) {
        return new MapSqlParameterSource()
                .addValue("modelVersion", modelVersion, Types.VARCHAR)
                .addValue("horizonMinutes", horizonMinutes, Types.SMALLINT)
                .addValue("cellSizeMeters", cellSizeMeters, Types.INTEGER);
    }

    private MapSqlParameterSource forecastRunFilters(
            String status,
            String runPurpose,
            String modelVersion,
            String sourceProfile
    ) {
        return new MapSqlParameterSource()
                .addValue("status", status, Types.VARCHAR)
                .addValue("runPurpose", runPurpose, Types.VARCHAR)
                .addValue("modelVersion", modelVersion, Types.VARCHAR)
                .addValue("sourceProfile", sourceProfile, Types.VARCHAR);
    }

    private MapSqlParameterSource forecastPointParameters(
            UUID forecastRunId,
            Instant from,
            Instant to,
            int horizonMinutes,
            SpatialBounds bounds,
            int limit
    ) {
        boolean bounded = bounds != null;
        return new MapSqlParameterSource()
                .addValue("forecastRunId", forecastRunId, Types.OTHER)
                .addValue("from", from.atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("to", to.atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("horizonMinutes", horizonMinutes, Types.SMALLINT)
                .addValue("bounded", bounded)
                .addValue("minLongitude", bounded ? bounds.minLongitude() : BigDecimal.ZERO)
                .addValue("minLatitude", bounded ? bounds.minLatitude() : BigDecimal.ZERO)
                .addValue("maxLongitude", bounded ? bounds.maxLongitude() : BigDecimal.ZERO)
                .addValue("maxLatitude", bounded ? bounds.maxLatitude() : BigDecimal.ZERO)
                .addValue("limit", limit);
    }

    private ProcessingStageRow processingStage(ResultSet resultSet) throws SQLException {
        return new ProcessingStageRow(
                uuid(resultSet, "run_id"),
                resultSet.getString("artifact_run_id"),
                resultSet.getString("run_type"),
                resultSet.getString("status"),
                resultSet.getString("source_profile"),
                resultSet.getString("dataset_version"),
                instant(resultSet, "source_cutoff"),
                instant(resultSet, "started_at"),
                instant(resultSet, "finished_at"),
                resultSet.getLong("rows_read"),
                resultSet.getLong("rows_written"),
                quality(resultSet)
        );
    }

    private ProcessingRunRow processingRun(ResultSet resultSet) throws SQLException {
        return new ProcessingRunRow(
                uuid(resultSet, "run_id"),
                resultSet.getString("artifact_run_id"),
                resultSet.getString("run_type"),
                resultSet.getString("status"),
                resultSet.getString("source_profile"),
                resultSet.getString("dataset_version"),
                instant(resultSet, "source_cutoff"),
                resultSet.getString("code_commit"),
                resultSet.getInt("attempt_no"),
                resultSet.getLong("rows_read"),
                resultSet.getLong("rows_written"),
                instant(resultSet, "created_at"),
                instant(resultSet, "started_at"),
                instant(resultSet, "finished_at"),
                resultSet.getString("error_code"),
                resultSet.getString("error_message"),
                quality(resultSet)
        );
    }

    private ModelVersionRow modelVersion(ResultSet resultSet) throws SQLException {
        return new ModelVersionRow(
                uuid(resultSet, "model_version_id"),
                resultSet.getString("model_name"),
                resultSet.getString("model_version"),
                resultSet.getString("model_family"),
                resultSet.getString("lifecycle_status"),
                resultSet.getString("approval_scope"),
                resultSet.getString("source_profile"),
                resultSet.getString("dataset_version"),
                resultSet.getString("demand_event_semantics"),
                resultSet.getString("feature_set_version"),
                resultSet.getString("grid_version"),
                resultSet.getInt("cell_size_meters"),
                resultSet.getInt("bucket_minutes"),
                instant(resultSet, "training_cutoff_utc"),
                resultSet.getString("artifact_sha256"),
                json(resultSet.getString("hyperparameters_json")),
                json(resultSet.getString("model_card_json")),
                instant(resultSet, "created_at"),
                instant(resultSet, "validated_at"),
                instant(resultSet, "approved_at"),
                instant(resultSet, "retired_at")
        );
    }

    private SelectedForecastRunRow selectedForecastRun(ResultSet resultSet) throws SQLException {
        return new SelectedForecastRunRow(
                uuid(resultSet, "forecast_run_id"),
                uuid(resultSet, "processing_run_id"),
                uuid(resultSet, "model_version_id"),
                resultSet.getString("model_version"),
                resultSet.getString("model_family"),
                resultSet.getString("feature_set_version"),
                resultSet.getString("approval_scope"),
                resultSet.getString("source_profile"),
                resultSet.getString("dataset_version"),
                resultSet.getString("demand_event_semantics"),
                resultSet.getString("run_purpose"),
                resultSet.getString("status"),
                resultSet.getInt("cell_size_meters"),
                instant(resultSet, "generated_at_utc"),
                instant(resultSet, "inference_cutoff_utc"),
                instant(resultSet, "finished_at"),
                instant(resultSet, "published_at"),
                resultSet.getLong("forecast_rows"),
                resultSet.getLong("evaluated_rows"),
                instant(resultSet, "latest_evaluated_at"),
                quality(resultSet)
        );
    }

    private ForecastPointRow forecastPoint(ResultSet resultSet) throws SQLException {
        int actual = resultSet.getInt("actual_demand");
        Integer actualDemand = resultSet.wasNull() ? null : actual;
        return new ForecastPointRow(
                resultSet.getString("cell_id"),
                resultSet.getString("geometry_json"),
                instant(resultSet, "target_bucket_start_utc"),
                resultSet.getInt("horizon_minutes"),
                resultSet.getBigDecimal("predicted_demand"),
                resultSet.getBigDecimal("prediction_lower"),
                resultSet.getBigDecimal("prediction_upper"),
                actualDemand,
                resultSet.getBigDecimal("absolute_error"),
                instant(resultSet, "evaluated_at")
        );
    }

    private EvaluationMetricRow evaluationMetric(ResultSet resultSet) throws SQLException {
        return new EvaluationMetricRow(
                uuid(resultSet, "forecast_run_id"),
                uuid(resultSet, "model_version_id"),
                resultSet.getString("model_version"),
                resultSet.getString("model_family"),
                resultSet.getString("approval_scope"),
                resultSet.getString("fold_key"),
                resultSet.getString("metric_name"),
                resultSet.getInt("horizon_minutes"),
                resultSet.getInt("cell_size_meters"),
                resultSet.getString("slice_type"),
                resultSet.getString("slice_key"),
                resultSet.getBigDecimal("metric_value"),
                resultSet.getLong("sample_count"),
                instant(resultSet, "created_at")
        );
    }

    private ForecastRunHistoryRow forecastRunHistory(ResultSet resultSet) throws SQLException {
        return new ForecastRunHistoryRow(
                uuid(resultSet, "forecast_run_id"),
                uuid(resultSet, "processing_run_id"),
                uuid(resultSet, "model_version_id"),
                resultSet.getString("model_version"),
                resultSet.getString("model_family"),
                resultSet.getString("approval_scope"),
                resultSet.getString("run_purpose"),
                resultSet.getString("status"),
                resultSet.getString("source_profile"),
                resultSet.getString("dataset_version"),
                resultSet.getString("demand_event_semantics"),
                resultSet.getString("grid_version"),
                resultSet.getInt("cell_size_meters"),
                resultSet.getInt("bucket_minutes"),
                instant(resultSet, "inference_cutoff_utc"),
                instant(resultSet, "generated_at_utc"),
                instant(resultSet, "finished_at"),
                instant(resultSet, "published_at"),
                resultSet.getString("error_code"),
                resultSet.getString("error_message"),
                resultSet.getLong("forecast_rows"),
                resultSet.getLong("evaluated_rows"),
                instant(resultSet, "latest_evaluated_at"),
                quality(resultSet)
        );
    }

    private QualityCounts quality(ResultSet resultSet) throws SQLException {
        return new QualityCounts(
                resultSet.getLong("quality_pass"),
                resultSet.getLong("quality_warn"),
                resultSet.getLong("quality_fail")
        );
    }

    private UUID uuid(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, UUID.class);
    }

    private Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp value = resultSet.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private JsonNode json(String value) throws SQLException {
        if (value == null) {
            return NullNode.getInstance();
        }
        try {
            return objectMapper.readTree(value);
        }
        catch (JsonProcessingException exception) {
            throw new SQLException("Stored analytics JSON is invalid", exception);
        }
    }
}
