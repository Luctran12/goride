package com.example.goride.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "GeoJSON demand forecast with optional actuals and prediction interval.")
public record ForecastDemandResponse(
        String type,
        ForecastResponseMetadata metadata,
        List<Feature> features
) {
    public record Feature(String type, Polygon geometry, Properties properties) {
    }

    public record Polygon(String type, List<List<List<BigDecimal>>> coordinates) {
    }

    public record Properties(
            String cellId,
            Instant targetBucketStart,
            int horizonMinutes,
            BigDecimal predictedDemand,
            BigDecimal predictionLower,
            BigDecimal predictionUpper,
            Integer actualDemand,
            BigDecimal absoluteError,
            Instant evaluatedAt,
            String evaluationStatus
    ) {
    }
}
