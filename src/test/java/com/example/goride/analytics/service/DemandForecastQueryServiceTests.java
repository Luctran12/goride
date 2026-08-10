package com.example.goride.analytics.service;

import com.example.goride.analytics.config.AnalyticsSpatialProperties;
import com.example.goride.analytics.config.DemandForecastServingProperties;
import com.example.goride.analytics.repository.DemandForecastQueryPort;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DemandForecastQueryServiceTests {
    private static final UUID FORECAST_RUN_ID = UUID.fromString(
            "791b9584-c448-516e-94b4-0ab50aa59e96"
    );
    private static final UUID PROCESSING_RUN_ID = UUID.fromString(
            "9246ef7e-59ff-5729-8a62-a28bbe7ccfc7"
    );
    private static final UUID MODEL_VERSION_ID = UUID.fromString(
            "057f564e-0412-5958-9291-6af0932326d1"
    );
    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");

    private DemandForecastQueryPort queryPort;
    private DemandForecastServingProperties properties;
    private DemandForecastQueryService service;

    @BeforeEach
    void setUp() {
        queryPort = mock(DemandForecastQueryPort.class);
        properties = new DemandForecastServingProperties();
        service = new DemandForecastQueryService(
                queryPort,
                properties,
                new AnalyticsSpatialProperties(),
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void labelsEvaluationForecastAsHistoricalResearchAndReturnsBackendValues() {
        var run = selectedEvaluationRun();
        when(queryPort.selectForecastRun(null, run.modelVersion(), "EVALUATION", 500))
                .thenReturn(Optional.of(run));
        when(queryPort.demandForecasts(
                eq(FORECAST_RUN_ID),
                any(),
                any(),
                eq(15),
                eq(null),
                anyInt()
        )).thenReturn(List.of(point("3.250000", 3, "0.250000")));

        var response = service.getDemandForecast(
                OffsetDateTime.parse("2014-06-01T00:00:00Z"),
                OffsetDateTime.parse("2014-06-01T01:00:00Z"),
                "UTC",
                15,
                500,
                run.modelVersion(),
                null,
                "evaluation",
                null,
                null,
                null,
                null
        );

        assertThat(response.metadata().availabilityStatus()).isEqualTo("AVAILABLE_RESEARCH");
        assertThat(response.metadata().availabilityReason())
                .isEqualTo("HISTORICAL_EVALUATION_NOT_OPERATIONAL");
        assertThat(response.metadata().freshnessStatus()).isEqualTo("HISTORICAL_EVALUATION");
        assertThat(response.metadata().approvalScope()).isEqualTo("RESEARCH_DEMONSTRATION");
        assertThat(response.metadata().demandUnit())
                .isEqualTo("trip_requests_per_15_minute_bucket");
        assertThat(response.features()).singleElement().satisfies(feature -> {
            assertThat(feature.geometry().type()).isEqualTo("Polygon");
            assertThat(feature.properties().predictedDemand()).isEqualByComparingTo("3.250000");
            assertThat(feature.properties().actualDemand()).isEqualTo(3);
            assertThat(feature.properties().absoluteError()).isEqualByComparingTo("0.250000");
            assertThat(feature.properties().evaluationStatus()).isEqualTo("ACTUAL_AVAILABLE");
        });
        assertThat(response.metadata().minimumAggregateCount()).isEqualTo(3);
        assertThat(response.metadata().suppressedActualRows()).isZero();
    }

    @Test
    void suppressesLowCountActualAndErrorAtTheServingBoundary() {
        var run = selectedEvaluationRun();
        when(queryPort.selectForecastRun(null, run.modelVersion(), "EVALUATION", 500))
                .thenReturn(Optional.of(run));
        when(queryPort.demandForecasts(
                eq(FORECAST_RUN_ID), any(), any(), eq(15), eq(null), anyInt()
        )).thenReturn(List.of(point("3.250000", 2, "1.250000")));

        var response = service.getDemandForecast(
                OffsetDateTime.parse("2014-06-01T00:00:00Z"),
                OffsetDateTime.parse("2014-06-01T01:00:00Z"),
                "UTC",
                15,
                500,
                run.modelVersion(),
                null,
                "EVALUATION",
                null,
                null,
                null,
                null
        );

        assertThat(response.features()).singleElement().satisfies(feature -> {
            assertThat(feature.properties().predictedDemand()).isEqualByComparingTo("3.250000");
            assertThat(feature.properties().actualDemand()).isNull();
            assertThat(feature.properties().absoluteError()).isNull();
            assertThat(feature.properties().evaluatedAt()).isNull();
            assertThat(feature.properties().evaluationStatus()).isEqualTo("ACTUAL_SUPPRESSED");
        });
        assertThat(response.metadata().minimumAggregateCount()).isEqualTo(3);
        assertThat(response.metadata().suppressedActualRows()).isEqualTo(1);
    }

    @Test
    void returnsExplicitUnavailableContractWhenNoRunMatches() {
        when(queryPort.selectForecastRun(null, null, "PUBLISHED", 500))
                .thenReturn(Optional.empty());

        var response = service.getDemandForecast(
                OffsetDateTime.parse("2026-08-10T10:00:00Z"),
                OffsetDateTime.parse("2026-08-10T11:00:00Z"),
                "Asia/Ho_Chi_Minh",
                30,
                500,
                null,
                null,
                "PUBLISHED",
                null,
                null,
                null,
                null
        );

        assertThat(response.metadata().availabilityStatus()).isEqualTo("UNAVAILABLE");
        assertThat(response.metadata().availabilityReason()).isEqualTo("NO_MATCHING_FORECAST");
        assertThat(response.features()).isEmpty();
    }

    @Test
    void labelsOldPublishedForecastAsStaleWithoutHidingItsData() {
        var research = selectedEvaluationRun();
        var published = new DemandForecastQueryPort.SelectedForecastRunRow(
                research.forecastRunId(),
                research.processingRunId(),
                research.modelVersionId(),
                research.modelVersion(),
                research.modelFamily(),
                research.featureSetVersion(),
                research.approvalScope(),
                research.sourceProfile(),
                research.datasetVersion(),
                research.demandEventSemantics(),
                "PUBLISHED",
                "PUBLISHED",
                500,
                NOW.minusSeconds(3600),
                NOW.minusSeconds(4500),
                NOW.minusSeconds(3590),
                NOW.minusSeconds(3590),
                1,
                0,
                null,
                research.quality()
        );
        when(queryPort.selectForecastRun(null, null, "PUBLISHED", 500))
                .thenReturn(Optional.of(published));
        when(queryPort.demandForecasts(
                eq(FORECAST_RUN_ID), any(), any(), eq(15), eq(null), anyInt()
        )).thenReturn(List.of(point("3", null, null)));

        var response = service.getDemandForecast(
                OffsetDateTime.ofInstant(NOW.minusSeconds(4500), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(NOW.minusSeconds(3500), ZoneOffset.UTC),
                "UTC",
                15,
                500,
                null,
                null,
                "PUBLISHED",
                null,
                null,
                null,
                null
        );

        assertThat(response.metadata().availabilityStatus()).isEqualTo("STALE");
        assertThat(response.metadata().freshnessStatus()).isEqualTo("STALE");
        assertThat(response.metadata().freshnessAgeSeconds()).isEqualTo(3590);
        assertThat(response.features()).hasSize(1);
    }

    @Test
    void validatesRangeHorizonCellBoundsAndResponseCost() {
        assertThatThrownBy(() -> properties.setMinimumActualDemandCount(2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 3");
        assertThatThrownBy(() -> service.getDemandForecast(
                OffsetDateTime.parse("2026-08-10T11:00:00Z"),
                OffsetDateTime.parse("2026-08-10T10:00:00Z"),
                "UTC",
                15,
                500,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        )).isInstanceOfSatisfying(BusinessException.class, exception -> {
            assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
            assertThat(exception.details()).containsKey("range");
        });

        assertThatThrownBy(() -> service.getForecastEvaluation(null, 45, 500))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.details()).containsKey("horizonMinutes")
                );
        assertThatThrownBy(() -> service.getForecastEvaluation(null, 15, 750))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.details()).containsKey("cellSizeMeters")
                );
        assertThatThrownBy(() -> service.getDemandForecast(
                OffsetDateTime.parse("2014-06-01T00:00:00Z"),
                OffsetDateTime.parse("2014-06-01T01:00:00Z"),
                "UTC",
                15,
                500,
                null,
                null,
                null,
                new BigDecimal("-8.7"),
                null,
                null,
                null
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.details()).containsKey("bounds")
        );

        properties.setMaximumForecastRows(1);
        var run = selectedEvaluationRun();
        when(queryPort.selectForecastRun(null, null, null, 500))
                .thenReturn(Optional.of(run));
        when(queryPort.demandForecasts(
                eq(FORECAST_RUN_ID), any(), any(), eq(15), eq(null), eq(2)
        )).thenReturn(List.of(
                point("1", 1, "0"),
                point("2", 1, "1")
        ));
        assertThatThrownBy(() -> service.getDemandForecast(
                OffsetDateTime.parse("2014-06-01T00:00:00Z"),
                OffsetDateTime.parse("2014-06-01T01:00:00Z"),
                "UTC",
                15,
                500,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.ANALYTICS_RESULT_TOO_LARGE)
        );
    }

    @Test
    void derivesEvaluationMetricsWhenPersistentEvaluationStoreIsEmpty() {
        when(queryPort.storedEvaluationMetrics(null, 15, 500, 1001))
                .thenReturn(List.of());
        var metric = new DemandForecastQueryPort.EvaluationMetricRow(
                FORECAST_RUN_ID,
                MODEL_VERSION_ID,
                "porto-hgb-v1",
                "GRADIENT_BOOSTED_TREES",
                "RESEARCH_DEMONSTRATION",
                "ACTUAL_BACKFILL",
                "MAE",
                15,
                500,
                null,
                null,
                new BigDecimal("0.62209200"),
                134,
                NOW
        );
        when(queryPort.derivedEvaluationMetrics(null, 15, 500, 1001))
                .thenReturn(List.of(metric));

        var response = service.getForecastEvaluation(null, 15, 500);

        assertThat(response.metricSource()).isEqualTo("DERIVED_FROM_BACKFILLED_ACTUALS");
        assertThat(response.metrics()).singleElement().satisfies(value -> {
            assertThat(value.metricName()).isEqualTo("MAE");
            assertThat(value.metricValue()).isEqualByComparingTo("0.62209200");
            assertThat(value.sampleCount()).isEqualTo(134);
        });
    }

    @Test
    void mapsDatabaseFailureToStableUnavailableError() {
        when(queryPort.latestProcessingStages(null))
                .thenThrow(new DataAccessResourceFailureException("offline"));

        assertThatThrownBy(() -> service.getProcessingStatus(null))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.ANALYTICS_DATA_UNAVAILABLE);
                    assertThat(exception.details()).containsEntry("component", "processingStatus");
                });
    }

    private DemandForecastQueryPort.SelectedForecastRunRow selectedEvaluationRun() {
        return new DemandForecastQueryPort.SelectedForecastRunRow(
                FORECAST_RUN_ID,
                PROCESSING_RUN_ID,
                MODEL_VERSION_ID,
                "porto-hgb-v1",
                "GRADIENT_BOOSTED_TREES",
                "demand-v1",
                "RESEARCH_DEMONSTRATION",
                "porto-thesis",
                "porto-kaggle-v1",
                "TRIP_STARTED_PROXY",
                "EVALUATION",
                "SUCCEEDED",
                500,
                Instant.parse("2026-08-10T10:26:01Z"),
                Instant.parse("2014-06-01T00:00:00Z"),
                Instant.parse("2026-08-10T10:26:02Z"),
                null,
                402,
                402,
                Instant.parse("2026-08-10T10:27:48Z"),
                new DemandForecastQueryPort.QualityCounts(4, 0, 0)
        );
    }

    private DemandForecastQueryPort.ForecastPointRow point(
            String predicted,
            Integer actual,
            String error
    ) {
        return new DemandForecastQueryPort.ForecastPointRow(
                "porto-grid-v1:3763:500:0:0",
                "{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1,0],[1,1],[0,1],[0,0]]]}",
                Instant.parse("2014-06-01T00:15:00Z"),
                15,
                new BigDecimal(predicted),
                null,
                null,
                actual,
                error == null ? null : new BigDecimal(error),
                actual == null ? null : NOW
        );
    }
}
