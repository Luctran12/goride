package com.example.goride.tracking.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record DriverLocationResponse(
        Long tripId,
        Long driverId,
        BigDecimal lat,
        BigDecimal lng,
        BigDecimal bearing,
        BigDecimal speed,
        Instant updatedAt
) {
}
