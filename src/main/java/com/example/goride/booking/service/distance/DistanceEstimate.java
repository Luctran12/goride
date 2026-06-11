package com.example.goride.booking.service.distance;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record DistanceEstimate(
        BigDecimal distanceKm,
        int durationMinutes
) {
    public DistanceEstimate {
        if (distanceKm == null || distanceKm.signum() <= 0) {
            throw new IllegalArgumentException("distanceKm must be positive");
        }
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("durationMinutes must be positive");
        }
        distanceKm = distanceKm.setScale(2, RoundingMode.HALF_UP);
    }
}
