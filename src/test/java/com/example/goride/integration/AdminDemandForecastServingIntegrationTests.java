package com.example.goride.integration;

import com.example.goride.analytics.service.DemandForecastQueryService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminDemandForecastServingIntegrationTests extends PostgresRedisIntegrationTest {
    private static final UUID TRAINING_RUN_ID = UUID.fromString(
            "2ad1e7d0-e6b4-5da0-bc40-110ea9015001"
    );
    private static final UUID PROCESSING_RUN_ID = UUID.fromString(
            "2ad1e7d0-e6b4-5da0-bc40-110ea9015002"
    );
    private static final UUID MODEL_VERSION_ID = UUID.fromString(
            "2ad1e7d0-e6b4-5da0-bc40-110ea9015003"
    );
    private static final UUID FORECAST_RUN_ID = UUID.fromString(
            "2ad1e7d0-e6b4-5da0-bc40-110ea9015004"
    );

    @Autowired
    private DemandForecastQueryService forecastService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    void applyForecastingSchema() throws IOException {
        Path release = Path.of("db", "releases", "20260808-admin-demand-forecasting");
        jdbcTemplate.execute(Files.readString(release.resolve("precheck.sql")));
        jdbcTemplate.execute(Files.readString(release.resolve("apply.sql")));
        jdbcTemplate.execute(Files.readString(release.resolve("verify.sql")));
        Path servingIndexes = Path.of(
                "db", "releases", "20260810-admin-demand-forecast-serving-indexes"
        );
        jdbcTemplate.execute(Files.readString(servingIndexes.resolve("precheck.sql")));
        jdbcTemplate.execute(Files.readString(servingIndexes.resolve("apply.sql")));
        jdbcTemplate.execute(Files.readString(servingIndexes.resolve("verify.sql")));
    }

    @BeforeEach
    void seedForecastReadModel() {
        jdbcTemplate.update(
                """
                        INSERT INTO analytics.processing_runs (
                            run_id, artifact_run_id, run_type, status,
                            source_profile, dataset_version, source_cutoff,
                            config_hash, code_commit, input_manifest,
                            rows_read, rows_written,
                            created_at, started_at, finished_at
                        ) VALUES (
                            ?, 'phase8-training-fixture', 'TRAINING', 'SUCCEEDED',
                            'porto-thesis', 'porto-kaggle-v1',
                            TIMESTAMPTZ '2014-06-01T00:00:00Z',
                            repeat('a', 64), repeat('1', 40), '{}'::JSONB,
                            100, 1,
                            CURRENT_TIMESTAMP - INTERVAL '5 minutes',
                            CURRENT_TIMESTAMP - INTERVAL '5 minutes',
                            CURRENT_TIMESTAMP - INTERVAL '4 minutes'
                        )
                        """,
                TRAINING_RUN_ID
        );
        jdbcTemplate.update(
                """
                        INSERT INTO analytics.model_versions (
                            model_version_id, model_name, model_version, model_family,
                            lifecycle_status, source_profile, dataset_version,
                            demand_event_semantics, feature_set_version, grid_version,
                            cell_size_meters, bucket_minutes, training_cutoff_utc,
                            training_run_id, artifact_uri, artifact_sha256,
                            hyperparameters, training_manifest,
                            created_at, validated_at, approved_at
                        ) VALUES (
                            ?, 'phase8-hgb', 'phase8-hgb-v1', 'GRADIENT_BOOSTED_TREES',
                            'APPROVED', 'porto-thesis', 'porto-kaggle-v1',
                            'TRIP_STARTED_PROXY', 'demand-v1', 'porto-grid-v1',
                            500, 15, TIMESTAMPTZ '2014-06-01T00:00:00Z',
                            ?, 'file:///not-exposed/model.joblib', repeat('b', 64),
                            '{"max_iter":100}'::JSONB,
                            '{"approvalScope":"RESEARCH_DEMONSTRATION","modelCard":{"title":"Phase 8 fixture"}}'::JSONB,
                            CURRENT_TIMESTAMP - INTERVAL '3 minutes',
                            CURRENT_TIMESTAMP - INTERVAL '3 minutes',
                            CURRENT_TIMESTAMP - INTERVAL '2 minutes'
                        )
                        """,
                MODEL_VERSION_ID,
                TRAINING_RUN_ID
        );
        jdbcTemplate.update(
                """
                        INSERT INTO analytics.processing_runs (
                            run_id, artifact_run_id, run_type, status,
                            source_profile, dataset_version, source_cutoff,
                            config_hash, code_commit, input_manifest,
                            rows_read, rows_written,
                            created_at, started_at, finished_at
                        ) VALUES (
                            ?, 'phase8-forecast-fixture', 'FORECAST', 'SUCCEEDED',
                            'porto-thesis', 'porto-kaggle-v1',
                            TIMESTAMPTZ '2014-06-01T00:00:00Z',
                            repeat('c', 64), repeat('2', 40), '{}'::JSONB,
                            2, 2,
                            CURRENT_TIMESTAMP - INTERVAL '90 seconds',
                            CURRENT_TIMESTAMP - INTERVAL '80 seconds',
                            CURRENT_TIMESTAMP - INTERVAL '40 seconds'
                        )
                        """,
                PROCESSING_RUN_ID
        );
        jdbcTemplate.update(
                """
                        INSERT INTO analytics.forecast_runs (
                            forecast_run_id, processing_run_id, model_version_id,
                            run_purpose, status, source_profile, dataset_version,
                            demand_event_semantics, grid_version, cell_size_meters,
                            bucket_minutes, inference_cutoff_utc, config_hash,
                            created_at, started_at, generated_at_utc, finished_at
                        ) VALUES (
                            ?, ?, ?, 'EVALUATION', 'SUCCEEDED',
                            'porto-thesis', 'porto-kaggle-v1', 'TRIP_STARTED_PROXY',
                            'porto-grid-v1', 500, 15,
                            TIMESTAMPTZ '2014-06-01T00:00:00Z', repeat('c', 64),
                            CURRENT_TIMESTAMP - INTERVAL '90 seconds',
                            CURRENT_TIMESTAMP - INTERVAL '80 seconds',
                            CURRENT_TIMESTAMP - INTERVAL '70 seconds',
                            CURRENT_TIMESTAMP - INTERVAL '40 seconds'
                        )
                        """,
                FORECAST_RUN_ID,
                PROCESSING_RUN_ID,
                MODEL_VERSION_ID
        );
        jdbcTemplate.update(
                """
                        INSERT INTO analytics.data_quality_results (
                            run_id, rule_code, severity, result_status,
                            records_checked, records_breached, threshold,
                            details, evaluated_at
                        ) VALUES (
                            ?, 'FORECAST_ROWS_PRESENT', 'FAIL', 'PASS',
                            2, 0, '{"minimum":1}'::JSONB,
                            '{"observed":2}'::JSONB, CURRENT_TIMESTAMP
                        )
                        """,
                PROCESSING_RUN_ID
        );
        jdbcTemplate.update(
                """
                        INSERT INTO analytics.demand_forecasts (
                            forecast_run_id, model_version_id, cell_id, cell_geometry,
                            cell_size_meters, generated_at_utc, inference_cutoff_utc,
                            target_bucket_start_utc, horizon_minutes,
                            predicted_demand, actual_demand, absolute_error, evaluated_at
                        )
                        SELECT ?, ?, values.cell_id,
                               ST_MakeEnvelope(
                                   values.min_lng, 41.140,
                                   values.max_lng, 41.145, 4326
                               )::geometry(Polygon, 4326),
                               500, f.generated_at_utc, f.inference_cutoff_utc,
                               TIMESTAMPTZ '2014-06-01T00:15:00Z', 15,
                               values.predicted, values.actual,
                               ABS(values.predicted - values.actual), CURRENT_TIMESTAMP
                        FROM analytics.forecast_runs f
                        CROSS JOIN (
                            VALUES
                                ('porto-grid-v1:3763:500:0:0', -8.620, -8.615, 3::NUMERIC, 2),
                                ('porto-grid-v1:3763:500:1:0', -8.615, -8.610, 5::NUMERIC, 3)
                        ) values(cell_id, min_lng, max_lng, predicted, actual)
                        WHERE f.forecast_run_id = ?
                        """,
                FORECAST_RUN_ID,
                MODEL_VERSION_ID,
                FORECAST_RUN_ID
        );
    }

    @Test
    void servesResearchForecastHotspotsQualityModelsAndDerivedMetrics() {
        var demand = forecastService.getDemandForecast(
                OffsetDateTime.parse("2014-06-01T00:00:00Z"),
                OffsetDateTime.parse("2014-06-01T01:00:00Z"),
                "UTC",
                15,
                500,
                "phase8-hgb-v1",
                null,
                "EVALUATION",
                null,
                null,
                null,
                null
        );
        assertThat(demand.metadata().availabilityStatus()).isEqualTo("AVAILABLE_RESEARCH");
        assertThat(demand.metadata().approvalScope()).isEqualTo("RESEARCH_DEMONSTRATION");
        assertThat(demand.features()).hasSize(2);
        assertThat(demand.features()).allSatisfy(feature -> {
            assertThat(feature.geometry().type()).isEqualTo("Polygon");
            assertThat(feature.geometry().coordinates().get(0)).hasSize(5);
            assertThat(feature.properties().evaluationStatus()).isEqualTo("ACTUAL_AVAILABLE");
        });

        var hotspots = forecastService.getForecastHotspots(
                OffsetDateTime.parse("2014-06-01T00:00:00Z"),
                OffsetDateTime.parse("2014-06-01T01:00:00Z"),
                "UTC",
                15,
                500,
                "phase8-hgb-v1",
                null,
                "EVALUATION",
                new java.math.BigDecimal("-8.63"),
                new java.math.BigDecimal("41.13"),
                new java.math.BigDecimal("-8.60"),
                new java.math.BigDecimal("41.16"),
                1
        );
        assertThat(hotspots.hotspots()).singleElement().satisfies(hotspot -> {
            assertThat(hotspot.rank()).isEqualTo(1);
            assertThat(hotspot.predictedDemand()).isEqualByComparingTo("5");
        });

        var evaluation = forecastService.getForecastEvaluation("phase8-hgb-v1", 15, 500);
        assertThat(evaluation.metricSource()).isEqualTo("DERIVED_FROM_BACKFILLED_ACTUALS");
        assertThat(evaluation.metrics()).extracting(metric -> metric.metricName())
                .containsExactly("MAE", "RMSE", "WAPE");
        assertThat(metric(evaluation.metrics(), "MAE").metricValue())
                .isEqualByComparingTo("1.5");
        assertThat(metric(evaluation.metrics(), "WAPE").metricValue())
                .isEqualByComparingTo("0.6");

        var quality = forecastService.getDataQuality(PROCESSING_RUN_ID);
        assertThat(quality.summary().pass()).isEqualTo(1);
        assertThat(quality.rules()).singleElement().satisfies(rule ->
                assertThat(rule.details().path("observed").asInt()).isEqualTo(2)
        );

        var models = forecastService.getModels("APPROVED", "porto-thesis", 0, 10);
        assertThat(models.items()).singleElement().satisfies(model -> {
            assertThat(model.approvalScope()).isEqualTo("RESEARCH_DEMONSTRATION");
            assertThat(model.modelCard().path("title").asText()).isEqualTo("Phase 8 fixture");
            assertThat(model.getClass().getRecordComponents())
                    .noneMatch(component -> component.getName().equals("artifactUri"));
        });

        var runs = forecastService.getForecastRuns(
                "SUCCEEDED", "EVALUATION", "phase8-hgb-v1", "porto-thesis", 0, 10
        );
        assertThat(runs.items()).singleElement().satisfies(run -> {
            assertThat(run.forecastRows()).isEqualTo(2);
            assertThat(run.evaluatedRows()).isEqualTo(2);
            assertThat(run.freshnessStatus()).isEqualTo("HISTORICAL_EVALUATION");
            assertThat(run.quality().pass()).isEqualTo(1);
        });
    }

    @Test
    void hotspotAndMapQueriesUseForecastTemporalAndSpatialIndexes() {
        jdbcTemplate.execute("ANALYZE analytics.demand_forecasts");
        jdbcTemplate.execute("SET LOCAL enable_seqscan = off");

        String hotspotPlan = plan("""
                EXPLAIN (COSTS OFF)
                SELECT predicted_demand
                FROM analytics.demand_forecasts
                WHERE forecast_run_id = '2ad1e7d0-e6b4-5da0-bc40-110ea9015004'::UUID
                  AND target_bucket_start_utc >= TIMESTAMPTZ '2014-06-01T00:00:00Z'
                  AND target_bucket_start_utc < TIMESTAMPTZ '2014-06-01T01:00:00Z'
                  AND horizon_minutes = 15
                  AND cell_size_meters = 500
                ORDER BY predicted_demand DESC, cell_id
                """);
        String mapPlan = plan("""
                EXPLAIN (COSTS OFF)
                SELECT cell_id
                FROM analytics.demand_forecasts
                WHERE cell_geometry && ST_MakeEnvelope(-8.63, 41.13, -8.60, 41.16, 4326)
                """);

        assertThat(hotspotPlan).contains("idx_demand_forecasts_hotspot_lookup");
        assertThat(mapPlan).contains("idx_demand_forecasts_geometry_gist");
    }

    private ForecastMetric metric(
            List<com.example.goride.analytics.dto.ForecastEvaluationResponse.Metric> metrics,
            String name
    ) {
        var found = metrics.stream()
                .filter(metric -> name.equals(metric.metricName()))
                .findFirst()
                .orElseThrow();
        return new ForecastMetric(found.metricValue());
    }

    private String plan(String sql) {
        return String.join("\n", jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> resultSet.getString(1)
        ));
    }

    private record ForecastMetric(java.math.BigDecimal metricValue) {
    }
}
