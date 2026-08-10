package com.example.goride.analytics.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Read-only model registry view. Artifact locations are never exposed.")
public record ModelVersionResponse(
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
