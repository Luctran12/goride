package com.example.goride.driver.dto;

import com.example.goride.booking.domain.TripStatus;
import com.example.goride.booking.dto.BookingLocationResponse;
import com.example.goride.booking.service.distance.RoutePlan;

import java.util.List;

public record DriverTripRouteResponse(
        Long tripId,
        TripStatus tripStatus,
        RouteDestinationType destinationType,
        BookingLocationResponse destination,
        long distanceMeters,
        long durationSeconds,
        DriverRouteGeometryResponse geometry,
        List<DriverRouteStepResponse> steps
) {
    public static DriverTripRouteResponse from(
            Long tripId,
            TripStatus tripStatus,
            RouteDestinationType destinationType,
            BookingLocationResponse destination,
            RoutePlan routePlan
    ) {
        return new DriverTripRouteResponse(
                tripId,
                tripStatus,
                destinationType,
                destination,
                routePlan.distanceMeters(),
                routePlan.durationSeconds(),
                DriverRouteGeometryResponse.from(routePlan.geometry()),
                routePlan.steps().stream().map(DriverRouteStepResponse::from).toList()
        );
    }
}
