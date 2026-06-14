package com.example.goride.driver.dto;

import com.example.goride.booking.service.distance.RouteStep;

import java.math.BigDecimal;

public record DriverRouteStepResponse(
        long distanceMeters,
        long durationSeconds,
        String roadName,
        String maneuverType,
        String maneuverModifier,
        BigDecimal longitude,
        BigDecimal latitude
) {
    public static DriverRouteStepResponse from(RouteStep step) {
        return new DriverRouteStepResponse(
                step.distanceMeters(),
                step.durationSeconds(),
                step.roadName(),
                step.maneuverType(),
                step.maneuverModifier(),
                step.longitude(),
                step.latitude()
        );
    }
}
