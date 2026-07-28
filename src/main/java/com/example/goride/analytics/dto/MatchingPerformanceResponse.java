package com.example.goride.analytics.dto;

import com.example.goride.analytics.model.AnalyticsSourceVariant;

import java.math.BigDecimal;
import java.time.Instant;

public record MatchingPerformanceResponse(
        Instant from,
        Instant to,
        String reportingTimezone,
        AnalyticsSourceVariant sourceVariant,
        Instant dataFreshnessAt,
        long matchingRuns,
        long terminalRuns,
        long matchedRuns,
        long noDriverRuns,
        long cancelledRuns,
        long failedRuns,
        BigDecimal matchingSuccessRate,
        Long averageMatchingDurationMs,
        Long p50MatchingDurationMs,
        Long p95MatchingDurationMs,
        BigDecimal averageSearchesPerRun,
        BigDecimal averageCandidatesPerRun,
        BigDecimal averageOffersPerRun,
        BigDecimal offerAcceptanceRate,
        BigDecimal offerRejectionRate,
        BigDecimal offerTimeoutRate,
        BigDecimal averageCandidateDistanceM
) {
}
