package com.example.goride.analytics.model;

import com.example.goride.driver.domain.VehicleType;

import java.time.Instant;
import java.time.ZoneId;

public record AnalyticsFilter(
        Instant from,
        Instant to,
        ZoneId reportingTimezone,
        VehicleType vehicleType,
        Long serviceAreaId
) {
}
