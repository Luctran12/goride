package com.example.goride.booking.service.distance;

import java.math.BigDecimal;

public record RouteStep(
        long distanceMeters,
        long durationSeconds,
        String roadName,
        String maneuverType,
        String maneuverModifier,
        BigDecimal longitude,
        BigDecimal latitude
) {
}
