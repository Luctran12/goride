package com.example.goride.driver.service;

import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.dto.BookingLocationResponse;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.booking.service.distance.Location;
import com.example.goride.booking.service.distance.RouteGeometryProvider;
import com.example.goride.booking.service.distance.RoutePlan;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.driver.dto.DriverTripRouteRequest;
import com.example.goride.driver.dto.DriverTripRouteResponse;
import com.example.goride.driver.dto.RouteDestinationType;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;

@Service
public class DriverTripRoutingService {
    private final TripRepository tripRepository;
    private final RouteGeometryProvider routeGeometryProvider;

    public DriverTripRoutingService(
            TripRepository tripRepository,
            RouteGeometryProvider routeGeometryProvider
    ) {
        this.tripRepository = tripRepository;
        this.routeGeometryProvider = routeGeometryProvider;
    }

    @Transactional(readOnly = true)
    public DriverTripRouteResponse route(Long driverId, Long tripId, DriverTripRouteRequest request) {
        Trip trip = tripRepository.findByIdAndDeletedAtIsNull(tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
        assertAssignedDriver(driverId, trip);

        Destination destination = destinationFor(trip);
        RoutePlan routePlan = routeGeometryProvider.route(
                new Location(request.latitude(), request.longitude()),
                toLocation(destination.point())
        );
        return DriverTripRouteResponse.from(
                trip.getId(),
                trip.getStatus(),
                destination.type(),
                BookingLocationResponse.of(destination.point(), destination.address()),
                routePlan
        );
    }

    private void assertAssignedDriver(Long driverId, Trip trip) {
        if (trip.getDriver() == null || !Objects.equals(trip.getDriver().getId(), driverId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only the assigned driver can request trip routing");
        }
    }

    private Destination destinationFor(Trip trip) {
        return switch (trip.getStatus()) {
            case ACCEPTED -> new Destination(
                    RouteDestinationType.PICKUP,
                    trip.getPickupLocation(),
                    trip.getPickupAddress()
            );
            case ARRIVED, IN_PROGRESS -> new Destination(
                    RouteDestinationType.DROPOFF,
                    trip.getDropoffLocation(),
                    trip.getDropoffAddress()
            );
            default -> throw new BusinessException(
                    ErrorCode.TRIP_ROUTE_NOT_AVAILABLE,
                    "Driver routing is available only for accepted, arrived, or in-progress trips"
            );
        };
    }

    private Location toLocation(Point point) {
        return new Location(
                BigDecimal.valueOf(point.getY()),
                BigDecimal.valueOf(point.getX())
        );
    }

    private record Destination(
            RouteDestinationType type,
            Point point,
            String address
    ) {
    }
}
