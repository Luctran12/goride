package com.example.goride.analytics.dto;

import com.example.goride.analytics.model.AnalyticsSourceVariant;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record DemandHeatmapResponse(
        String type,
        Metadata metadata,
        List<Feature> features
) {
    public record Metadata(
            Instant from,
            Instant to,
            String reportingTimezone,
            int cellSizeMeters,
            AnalyticsSourceVariant sourceVariant,
            Instant dataFreshnessAt
    ) {
    }

    public record Feature(
            String type,
            Polygon geometry,
            Properties properties
    ) {
    }

    public record Polygon(
            String type,
            List<List<List<BigDecimal>>> coordinates
    ) {
    }

    public record Properties(
            String cellId,
            long tripRequests,
            long completedTripsByRequestCohort,
            BigDecimal completionRate
    ) {
    }
}
