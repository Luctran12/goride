package com.example.goride.analytics.repository;

import com.example.goride.analytics.model.SpatialBounds;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DemandForecastQueryPort {
    List<ProcessingStageRow> latestProcessingStages(String sourceProfile);

    List<ProcessingRunRow> processingRuns(
            String runType,
            String status,
            String sourceProfile,
            int offset,
            int limit
    );

    long countProcessingRuns(String runType, String status, String sourceProfile);

    Optional<ProcessingRunSummaryRow> processingRun(UUID runId);

    List<QualityRuleRow> qualityRules(UUID runId);

    List<ModelVersionRow> modelVersions(
            String lifecycleStatus,
            String sourceProfile,
            int offset,
            int limit
    );

    long countModelVersions(String lifecycleStatus, String sourceProfile);

    Optional<SelectedForecastRunRow> selectForecastRun(
            UUID forecastRunId,
            String modelVersion,
            String runPurpose,
            int cellSizeMeters
    );

    List<ForecastPointRow> demandForecasts(
            UUID forecastRunId,
            Instant from,
            Instant to,
            int horizonMinutes,
            SpatialBounds bounds,
            int limit
    );

    List<ForecastPointRow> forecastHotspots(
            UUID forecastRunId,
            Instant from,
            Instant to,
            int horizonMinutes,
            SpatialBounds bounds,
            int limit
    );

    List<EvaluationMetricRow> storedEvaluationMetrics(
            String modelVersion,
            Integer horizonMinutes,
            Integer cellSizeMeters,
            int limit
    );

    List<EvaluationMetricRow> derivedEvaluationMetrics(
            String modelVersion,
            Integer horizonMinutes,
            Integer cellSizeMeters,
            int limit
    );

    List<ForecastRunHistoryRow> forecastRuns(
            String status,
            String runPurpose,
            String modelVersion,
            String sourceProfile,
            int offset,
            int limit
    );

    long countForecastRuns(
            String status,
            String runPurpose,
            String modelVersion,
            String sourceProfile
    );

    record QualityCounts(long pass, long warn, long fail) {
    }

    record ProcessingStageRow(
            UUID runId,
            String artifactRunId,
            String runType,
            String status,
            String sourceProfile,
            String datasetVersion,
            Instant sourceCutoff,
            Instant startedAt,
            Instant finishedAt,
            long rowsRead,
            long rowsWritten,
            QualityCounts quality
    ) {
    }

    record ProcessingRunRow(
            UUID runId,
            String artifactRunId,
            String runType,
            String status,
            String sourceProfile,
            String datasetVersion,
            Instant sourceCutoff,
            String codeCommit,
            int attemptNo,
            long rowsRead,
            long rowsWritten,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt,
            String errorCode,
            String errorMessage,
            QualityCounts quality
    ) {
    }

    record ProcessingRunSummaryRow(
            UUID runId,
            String runType,
            String status,
            String sourceProfile,
            String datasetVersion,
            Instant sourceCutoff
    ) {
    }

    record QualityRuleRow(
            String ruleCode,
            String scopeKey,
            String severity,
            String resultStatus,
            long recordsChecked,
            long recordsBreached,
            BigDecimal metricValue,
            JsonNode threshold,
            JsonNode details,
            Instant evaluatedAt
    ) {
    }

    record ModelVersionRow(
            UUID modelVersionId,
            String modelName,
            String modelVersion,
            String modelFamily,
            String lifecycleStatus,
            String approvalScope,
            String sourceProfile,
            String datasetVersion,
            String demandEventSemantics,
            String featureSetVersion,
            String gridVersion,
            int cellSizeMeters,
            int bucketMinutes,
            Instant trainingCutoff,
            String artifactSha256,
            JsonNode hyperparameters,
            JsonNode modelCard,
            Instant createdAt,
            Instant validatedAt,
            Instant approvedAt,
            Instant retiredAt
    ) {
    }

    record SelectedForecastRunRow(
            UUID forecastRunId,
            UUID processingRunId,
            UUID modelVersionId,
            String modelVersion,
            String modelFamily,
            String featureSetVersion,
            String approvalScope,
            String sourceProfile,
            String datasetVersion,
            String demandEventSemantics,
            String runPurpose,
            String status,
            int cellSizeMeters,
            Instant generatedAt,
            Instant inferenceCutoff,
            Instant finishedAt,
            Instant publishedAt,
            long forecastRows,
            long evaluatedRows,
            Instant latestEvaluatedAt,
            QualityCounts quality
    ) {
    }

    record ForecastPointRow(
            String cellId,
            String geometryJson,
            Instant targetBucketStart,
            int horizonMinutes,
            BigDecimal predictedDemand,
            BigDecimal predictionLower,
            BigDecimal predictionUpper,
            Integer actualDemand,
            BigDecimal absoluteError,
            Instant evaluatedAt
    ) {
    }

    record EvaluationMetricRow(
            UUID forecastRunId,
            UUID modelVersionId,
            String modelVersion,
            String modelFamily,
            String approvalScope,
            String foldKey,
            String metricName,
            int horizonMinutes,
            int cellSizeMeters,
            String sliceType,
            String sliceKey,
            BigDecimal metricValue,
            long sampleCount,
            Instant createdAt
    ) {
    }

    record ForecastRunHistoryRow(
            UUID forecastRunId,
            UUID processingRunId,
            UUID modelVersionId,
            String modelVersion,
            String modelFamily,
            String approvalScope,
            String runPurpose,
            String status,
            String sourceProfile,
            String datasetVersion,
            String demandEventSemantics,
            String gridVersion,
            int cellSizeMeters,
            int bucketMinutes,
            Instant inferenceCutoff,
            Instant generatedAt,
            Instant finishedAt,
            Instant publishedAt,
            String errorCode,
            String errorMessage,
            long forecastRows,
            long evaluatedRows,
            Instant latestEvaluatedAt,
            QualityCounts quality
    ) {
    }
}
