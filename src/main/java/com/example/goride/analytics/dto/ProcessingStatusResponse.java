package com.example.goride.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Latest processing run for each analytics pipeline stage.")
public record ProcessingStatusResponse(
        Instant generatedAt,
        long staleAfterSeconds,
        List<Stage> stages
) {
    public record Stage(
            UUID runId,
            String artifactRunId,
            String runType,
            String status,
            String sourceProfile,
            String datasetVersion,
            Instant sourceCutoff,
            Instant startedAt,
            Instant finishedAt,
            Long ageSeconds,
            String freshnessStatus,
            long rowsRead,
            long rowsWritten,
            QualitySummary quality
    ) {
    }

    public record QualitySummary(long pass, long warn, long fail) {
    }
}
