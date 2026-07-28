package com.example.goride.analytics.dto;

import com.example.goride.analytics.model.AnalyticsSourceVariant;

import java.time.Instant;
import java.util.List;

public record MatchingFunnelResponse(
        Instant from,
        Instant to,
        String reportingTimezone,
        AnalyticsSourceVariant sourceVariant,
        Instant dataFreshnessAt,
        List<Step> steps
) {
    public record Step(
            String name,
            String unit,
            long count
    ) {
    }
}
