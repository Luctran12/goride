package com.example.goride.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public final class AdminAnalyticsApiResponses {
    private AdminAnalyticsApiResponses() {
    }

    @Schema(
            name = "AnalyticsOverviewEnvelope",
            description = "Successful overview response.",
            requiredProperties = {"success", "data", "message", "timestamp"}
    )
    public record Overview(
            @Schema(description = "Always true.", example = "true")
            boolean success,
            AnalyticsOverviewResponse data,
            @Schema(description = "Stable success summary.", example = "OK")
            String message,
            @Schema(description = "UTC envelope creation time.")
            Instant timestamp
    ) {
    }

    @Schema(
            name = "DemandTimeseriesEnvelope",
            description = "Successful demand-timeseries response.",
            requiredProperties = {"success", "data", "message", "timestamp"}
    )
    public record DemandTimeseries(
            @Schema(description = "Always true.", example = "true")
            boolean success,
            DemandTimeseriesResponse data,
            @Schema(description = "Stable success summary.", example = "OK")
            String message,
            @Schema(description = "UTC envelope creation time.")
            Instant timestamp
    ) {
    }

    @Schema(
            name = "SupplyTimeseriesEnvelope",
            description = "Successful supply-timeseries response.",
            requiredProperties = {"success", "data", "message", "timestamp"}
    )
    public record SupplyTimeseries(
            @Schema(description = "Always true.", example = "true")
            boolean success,
            SupplyTimeseriesResponse data,
            @Schema(description = "Stable success summary.", example = "OK")
            String message,
            @Schema(description = "UTC envelope creation time.")
            Instant timestamp
    ) {
    }

    @Schema(
            name = "DemandHeatmapEnvelope",
            description = "Successful heatmap response.",
            requiredProperties = {"success", "data", "message", "timestamp"}
    )
    public record DemandHeatmap(
            @Schema(description = "Always true.", example = "true")
            boolean success,
            DemandHeatmapResponse data,
            @Schema(description = "Stable success summary.", example = "OK")
            String message,
            @Schema(description = "UTC envelope creation time.")
            Instant timestamp
    ) {
    }

    @Schema(
            name = "MatchingPerformanceEnvelope",
            description = "Successful matching-performance response.",
            requiredProperties = {"success", "data", "message", "timestamp"}
    )
    public record MatchingPerformance(
            @Schema(description = "Always true.", example = "true")
            boolean success,
            MatchingPerformanceResponse data,
            @Schema(description = "Stable success summary.", example = "OK")
            String message,
            @Schema(description = "UTC envelope creation time.")
            Instant timestamp
    ) {
    }

    @Schema(
            name = "MatchingFunnelEnvelope",
            description = "Successful matching-funnel response.",
            requiredProperties = {"success", "data", "message", "timestamp"}
    )
    public record MatchingFunnel(
            @Schema(description = "Always true.", example = "true")
            boolean success,
            MatchingFunnelResponse data,
            @Schema(description = "Stable success summary.", example = "OK")
            String message,
            @Schema(description = "UTC envelope creation time.")
            Instant timestamp
    ) {
    }
}
