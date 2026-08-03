package com.example.goride.driver.service;

import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.dto.BookingLocationResponse;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.booking.service.distance.Location;
import com.example.goride.booking.service.distance.RouteGeometry;
import com.example.goride.booking.service.distance.RouteGeometryProvider;
import com.example.goride.booking.service.distance.RoutePlan;
import com.example.goride.booking.service.distance.RoutingProperties;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.driver.dto.DriverRouteSource;
import com.example.goride.driver.dto.DriverTripRouteRequest;
import com.example.goride.driver.dto.DriverTripRouteResponse;
import com.example.goride.driver.dto.RouteDestinationType;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
public class DriverTripRoutingService {
    private static final Logger log = LoggerFactory.getLogger(DriverTripRoutingService.class);
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private static final double FALLBACK_SPEED_METERS_PER_SECOND = 25.0 / 3.6;

    private final TripRepository tripRepository;
    private final RouteGeometryProvider routeGeometryProvider;
    private final RoutingProperties routingProperties;

    public DriverTripRoutingService(
            TripRepository tripRepository,
            RouteGeometryProvider routeGeometryProvider,
            RoutingProperties routingProperties
    ) {
        this.tripRepository = tripRepository;
        this.routeGeometryProvider = routeGeometryProvider;
        this.routingProperties = routingProperties;
    }

    @Transactional(readOnly = true)
    public DriverTripRouteResponse route(Long driverId, Long tripId, DriverTripRouteRequest request) {
        Trip trip = tripRepository.findByIdAndDeletedAtIsNull(tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
        assertAssignedDriver(driverId, trip);

        Destination destination = destinationFor(trip);
        Location origin = new Location(request.latitude(), request.longitude());
        Location destinationLocation = toLocation(destination.point());
        ResolvedRoute resolvedRoute = resolveRoute(driverId, tripId, origin, destinationLocation);
        return DriverTripRouteResponse.from(
                trip.getId(),
                trip.getStatus(),
                destination.type(),
                BookingLocationResponse.of(destination.point(), destination.address()),
                resolvedRoute.routePlan(),
                resolvedRoute.source()
        );
    }

    private ResolvedRoute resolveRoute(
            Long driverId,
            Long tripId,
            Location origin,
            Location destination
    ) {
        try {
            return new ResolvedRoute(
                    routeGeometryProvider.route(origin, destination),
                    DriverRouteSource.PROVIDER
            );
        } catch (BusinessException exception) {
            if (exception.errorCode() != ErrorCode.ROUTING_PROVIDER_ERROR
                    || !routingProperties.isFallbackEnabled()) {
                throw exception;
            }
            log.warn(
                    "Driver route provider failed; returning straight-line fallback: driverId={}, tripId={}, reason={}",
                    driverId,
                    tripId,
                    exception.getMessage()
            );
            return new ResolvedRoute(
                    straightLineRoute(origin, destination),
                    DriverRouteSource.STRAIGHT_LINE_FALLBACK
            );
        }
    }

    private RoutePlan straightLineRoute(Location origin, Location destination) {
        long distanceMeters = Math.max(1L, Math.round(haversineMeters(origin, destination)));
        long durationSeconds = Math.max(
                1L,
                (long) Math.ceil(distanceMeters / FALLBACK_SPEED_METERS_PER_SECOND)
        );
        return new RoutePlan(
                distanceMeters,
                durationSeconds,
                new RouteGeometry("LineString", List.of(
                        List.of(origin.longitude(), origin.latitude()),
                        List.of(destination.longitude(), destination.latitude())
                )),
                List.of()
        );
    }

    private double haversineMeters(Location origin, Location destination) {
        double originLatitude = Math.toRadians(origin.latitude().doubleValue());
        double destinationLatitude = Math.toRadians(destination.latitude().doubleValue());
        double latitudeDelta = destinationLatitude - originLatitude;
        double longitudeDelta = Math.toRadians(
                destination.longitude().doubleValue() - origin.longitude().doubleValue()
        );
        double haversine = Math.pow(Math.sin(latitudeDelta / 2), 2)
                + Math.cos(originLatitude) * Math.cos(destinationLatitude)
                * Math.pow(Math.sin(longitudeDelta / 2), 2);
        double clampedHaversine = Math.max(0, Math.min(1, haversine));
        double centralAngle = 2 * Math.atan2(Math.sqrt(clampedHaversine), Math.sqrt(1 - clampedHaversine));
        return EARTH_RADIUS_METERS * centralAngle;
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

    private record ResolvedRoute(RoutePlan routePlan, DriverRouteSource source) {
    }
}
