package com.example.goride.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Server-computed or persisted model evaluation metrics.")
public record ForecastEvaluationResponse(
        Instant generatedAt,
        String metricSource,
        String demandUnit,
        List<Metric> metrics
) {
    public record Metric(
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
}
