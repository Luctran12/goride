package com.example.goride.analytics.model;

import java.math.BigDecimal;

public record SpatialBounds(
        BigDecimal minLongitude,
        BigDecimal minLatitude,
        BigDecimal maxLongitude,
        BigDecimal maxLatitude
) {
}
