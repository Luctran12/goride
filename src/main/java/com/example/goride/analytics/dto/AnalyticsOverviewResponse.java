package com.example.goride.analytics.dto;

import com.example.goride.analytics.model.AnalyticsSourceVariant;

import java.math.BigDecimal;
import java.time.Instant;

public record AnalyticsOverviewResponse(
        Instant from,
        Instant to,
        String reportingTimezone,
        AnalyticsSourceVariant sourceVariant,
        Instant dataFreshnessAt,
        long tripRequests,
        long completedTrips,
        long completedTripsByRequestCohort,
        long cancelledTrips,
        long noDriverTrips,
        BigDecimal completionRate,
        long completedPayments,
        BigDecimal completedRevenue,
        long matchingRuns,
        long terminalRuns,
        BigDecimal matchingSuccessRate,
        Long averageMatchingDurationMs,
        Long p50MatchingDurationMs,
        Long p95MatchingDurationMs
) {
}
