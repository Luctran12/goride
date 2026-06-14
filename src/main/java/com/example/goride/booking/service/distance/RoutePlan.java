package com.example.goride.booking.service.distance;

import java.util.List;

public record RoutePlan(
        long distanceMeters,
        long durationSeconds,
        RouteGeometry geometry,
        List<RouteStep> steps
) {
}
