package com.example.goride.booking.dto;

import com.example.goride.booking.domain.Trip;
import com.example.goride.tracking.domain.TripLocationHistory;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AdminTripRouteResponse(
        Long tripId,
        RouteType routeType,
        BookingLocationResponse pickup,
        BookingLocationResponse dropoff,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Geometry geometry,
        List<RoutePoint> points
) {
    public static AdminTripRouteResponse from(Trip trip, List<TripLocationHistory> history) {
        List<RoutePoint> points = history.stream()
                .map(RoutePoint::from)
                .toList();
        Geometry geometry = points.size() < 2
                ? null
                : new Geometry(
                        "LineString",
                        points.stream()
                                .map(point -> List.of(point.lng(), point.lat()))
                                .toList()
                );

        return new AdminTripRouteResponse(
                trip.getId(),
                points.isEmpty() ? RouteType.UNAVAILABLE : RouteType.ACTUAL,
                BookingLocationResponse.of(trip.getPickupLocation(), trip.getPickupAddress()),
                BookingLocationResponse.of(trip.getDropoffLocation(), trip.getDropoffAddress()),
                geometry,
                List.copyOf(points)
        );
    }

    public enum RouteType {
        ACTUAL,
        UNAVAILABLE
    }

    public record Geometry(
            String type,
            List<List<BigDecimal>> coordinates
    ) {
    }

    public record RoutePoint(
            BigDecimal lat,
            BigDecimal lng,
            BigDecimal bearing,
            BigDecimal speed,
            Instant recordedAt
    ) {
        private static RoutePoint from(TripLocationHistory history) {
            return new RoutePoint(
                    BigDecimal.valueOf(history.getLocation().getY()),
                    BigDecimal.valueOf(history.getLocation().getX()),
                    history.getBearing(),
                    history.getSpeed(),
                    history.getRecordedAt()
            );
        }
    }
}
