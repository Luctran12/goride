package com.example.goride.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Forecast lineage, availability, research scope and freshness metadata.")
public record ForecastResponseMetadata(
        String availabilityStatus,
        String availabilityReason,
        String freshnessStatus,
        Long freshnessAgeSeconds,
        UUID forecastRunId,
        UUID modelVersionId,
        String modelVersion,
        String featureSetVersion,
        String approvalScope,
        String sourceProfile,
        String datasetVersion,
        String demandEventSemantics,
        String runPurpose,
        Instant generatedAt,
        Instant inferenceCutoff,
        Instant dataFreshnessAt,
        Instant from,
        Instant to,
        String reportingTimezone,
        Integer cellSizeMeters,
        Integer horizonMinutes,
        String demandUnit,
        long forecastRows,
        long evaluatedRows,
        int minimumAggregateCount,
        long suppressedActualRows,
        ProcessingStatusResponse.QualitySummary quality
) {
}
