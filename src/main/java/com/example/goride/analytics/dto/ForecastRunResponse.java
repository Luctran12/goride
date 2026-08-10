package com.example.goride.analytics.dto;

import java.time.Instant;
import java.util.UUID;

public record ForecastRunResponse(
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
        String freshnessStatus,
        Long freshnessAgeSeconds,
        ProcessingStatusResponse.QualitySummary quality
) {
}
