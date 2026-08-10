package com.example.goride.analytics.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Backend-computed data-quality summary and individual rule outcomes.")
public record DataQualityResponse(
        UUID runId,
        String runType,
        String runStatus,
        String sourceProfile,
        String datasetVersion,
        Instant sourceCutoff,
        ProcessingStatusResponse.QualitySummary summary,
        List<Rule> rules
) {
    public record Rule(
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
}
