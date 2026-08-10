package com.example.goride.analytics.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ForecastHotspotsResponse(
        ForecastResponseMetadata metadata,
        List<Hotspot> hotspots
) {
    public record Hotspot(
            int rank,
            String cellId,
            ForecastDemandResponse.Polygon geometry,
            Instant targetBucketStart,
            BigDecimal predictedDemand,
            BigDecimal predictionLower,
            BigDecimal predictionUpper,
            Integer actualDemand,
            BigDecimal absoluteError,
            String evaluationStatus
    ) {
    }
}
