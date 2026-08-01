package com.example.goride.analytics.dto;

import com.example.goride.analytics.model.AnalyticsSourceVariant;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(
        name = "MatchingPerformance",
        description = "Backend-computed matching run, duration, offer and candidate metrics. "
                + "The frontend must not recompute percentiles or rates.",
        requiredProperties = {
                "from", "to", "reportingTimezone", "sourceVariant", "dataFreshnessAt",
                "matchingRuns", "terminalRuns", "matchedRuns", "noDriverRuns",
                "cancelledRuns", "failedRuns", "matchingSuccessRate",
                "averageMatchingDurationMs", "p50MatchingDurationMs", "p95MatchingDurationMs",
                "averageSearchesPerRun", "averageCandidatesPerRun", "averageOffersPerRun",
                "offerAcceptanceRate", "offerRejectionRate", "offerTimeoutRate",
                "averageCandidateDistanceM"
        },
        example = """
                {
                  "from": "2026-06-30T17:00:00Z",
                  "to": "2026-07-07T17:00:00Z",
                  "reportingTimezone": "Asia/Ho_Chi_Minh",
                  "sourceVariant": "DIRECT",
                  "dataFreshnessAt": "2026-07-07T17:00:00Z",
                  "matchingRuns": 1000,
                  "terminalRuns": 990,
                  "matchedRuns": 900,
                  "noDriverRuns": 60,
                  "cancelledRuns": 25,
                  "failedRuns": 5,
                  "matchingSuccessRate": 0.9091,
                  "averageMatchingDurationMs": 10320,
                  "p50MatchingDurationMs": 8200,
                  "p95MatchingDurationMs": 26400,
                  "averageSearchesPerRun": 1.42,
                  "averageCandidatesPerRun": 4.21,
                  "averageOffersPerRun": 1.36,
                  "offerAcceptanceRate": 0.6680,
                  "offerRejectionRate": 0.2040,
                  "offerTimeoutRate": 0.1280,
                  "averageCandidateDistanceM": 1450.25
                }
                """
)
public record MatchingPerformanceResponse(
        @Schema(description = "Inclusive normalized UTC range boundary.")
        Instant from,
        @Schema(description = "Exclusive normalized UTC range boundary.")
        Instant to,
        @Schema(description = "IANA timezone used for calendar semantics.")
        String reportingTimezone,
        @Schema(description = "Physical query source used for this response.")
        AnalyticsSourceVariant sourceVariant,
        @Schema(description = "Database snapshot cutoff represented by the response.")
        Instant dataFreshnessAt,
        @Schema(description = "Matching runs started in [from, to). Unit: runs.", example = "1000")
        long matchingRuns,
        @Schema(description = "Terminal runs finished in [from, to). Unit: runs.", example = "990")
        long terminalRuns,
        @Schema(description = "Terminal MATCHED runs. Unit: runs.", example = "900")
        long matchedRuns,
        @Schema(description = "Terminal NO_DRIVER runs. Unit: runs.", example = "60")
        long noDriverRuns,
        @Schema(description = "Terminal CANCELLED runs. Unit: runs.", example = "25")
        long cancelledRuns,
        @Schema(description = "Terminal FAILED runs. Unit: runs.", example = "5")
        long failedRuns,
        @Schema(
                description = "matchedRuns / terminalRuns. Unit: ratio; scale: 4; "
                        + "null when terminalRuns is zero.",
                example = "0.9091",
                minimum = "0",
                maximum = "1",
                types = {"number", "null"},
                nullable = true
        )
        BigDecimal matchingSuccessRate,
        @Schema(
                description = "Mean terminal duration. Unit: milliseconds; null for no terminal "
                        + "runs.",
                example = "10320",
                format = "int64",
                types = {"integer", "null"},
                nullable = true
        )
        Long averageMatchingDurationMs,
        @Schema(
                description = "Continuous P50 terminal duration. Unit: milliseconds; null for no "
                        + "terminal runs.",
                example = "8200",
                format = "int64",
                types = {"integer", "null"},
                nullable = true
        )
        Long p50MatchingDurationMs,
        @Schema(
                description = "Continuous P95 terminal duration. Unit: milliseconds; null for no "
                        + "terminal runs.",
                example = "26400",
                format = "int64",
                types = {"integer", "null"},
                nullable = true
        )
        Long p95MatchingDurationMs,
        @Schema(
                description = "Mean cumulative searches over terminal runs. Scale: 2; null for no "
                        + "terminal runs.",
                example = "1.42",
                types = {"number", "null"},
                nullable = true
        )
        BigDecimal averageSearchesPerRun,
        @Schema(
                description = "Mean cumulative candidates over terminal runs. Scale: 2; null for "
                        + "no terminal runs.",
                example = "4.21",
                types = {"number", "null"},
                nullable = true
        )
        BigDecimal averageCandidatesPerRun,
        @Schema(
                description = "Persisted offers in terminal runs / terminal runs. Scale: 2; null "
                        + "for no terminal runs.",
                example = "1.36",
                types = {"number", "null"},
                nullable = true
        )
        BigDecimal averageOffersPerRun,
        @Schema(
                description = "Accepted terminal offers / terminal offers. Unit: ratio; scale: 4; "
                        + "null when no terminal offer exists.",
                example = "0.6680",
                minimum = "0",
                maximum = "1",
                types = {"number", "null"},
                nullable = true
        )
        BigDecimal offerAcceptanceRate,
        @Schema(
                description = "Rejected terminal offers / terminal offers. Unit: ratio; scale: 4; "
                        + "null when no terminal offer exists.",
                example = "0.2040",
                minimum = "0",
                maximum = "1",
                types = {"number", "null"},
                nullable = true
        )
        BigDecimal offerRejectionRate,
        @Schema(
                description = "Timed-out terminal offers / terminal offers. Unit: ratio; scale: 4; "
                        + "null when no terminal offer exists.",
                example = "0.1280",
                minimum = "0",
                maximum = "1",
                types = {"number", "null"},
                nullable = true
        )
        BigDecimal offerTimeoutRate,
        @Schema(
                description = "Mean non-negative offered candidate distance. Unit: meters; "
                        + "scale: 2; null when no distance exists.",
                example = "1450.25",
                types = {"number", "null"},
                nullable = true
        )
        BigDecimal averageCandidateDistanceM
) {
}
