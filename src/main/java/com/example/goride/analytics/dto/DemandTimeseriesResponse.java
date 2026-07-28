package com.example.goride.analytics.dto;

import com.example.goride.analytics.model.AnalyticsBucket;
import com.example.goride.analytics.model.AnalyticsSourceVariant;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

public record DemandTimeseriesResponse(
        Instant from,
        Instant to,
        String reportingTimezone,
        AnalyticsBucket bucket,
        AnalyticsSourceVariant sourceVariant,
        Instant dataFreshnessAt,
        List<Point> points
) {
    public record Point(
            OffsetDateTime bucketStart,
            long tripRequests,
            long completedTripsByRequestCohort,
            BigDecimal completionRate
    ) {
    }
}
