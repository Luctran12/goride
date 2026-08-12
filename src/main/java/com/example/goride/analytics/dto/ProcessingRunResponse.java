package com.example.goride.analytics.dto;

import java.time.Instant;
import java.util.UUID;

public record ProcessingRunResponse(
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
        ProcessingStatusResponse.QualitySummary quality
) {
}
