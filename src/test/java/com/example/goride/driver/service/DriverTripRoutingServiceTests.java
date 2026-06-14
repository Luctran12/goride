package com.example.goride.driver.service;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.booking.domain.PricingConfig;
import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.booking.service.distance.Location;
import com.example.goride.booking.service.distance.RouteGeometry;
import com.example.goride.booking.service.distance.RouteGeometryProvider;
import com.example.goride.booking.service.distance.RoutePlan;
import com.example.goride.booking.service.distance.RouteStep;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.driver.domain.VehicleType;
import com.example.goride.driver.dto.DriverTripRouteRequest;
import com.example.goride.driver.dto.RouteDestinationType;
import com.example.goride.user.domain.User;
import com.example.goride.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverTripRoutingServiceTests {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final DriverTripRouteRequest CURRENT_LOCATION =
            new DriverTripRouteRequest(BigDecimal.valueOf(10.7600), BigDecimal.valueOf(106.6900));

    @Mock
    private TripRepository tripRepository;

    @Mock
    private RouteGeometryProvider routeGeometryProvider;

    private DriverTripRoutingService service;

    @BeforeEach
    void setUp() {
        service = new DriverTripRoutingService(tripRepository, routeGeometryProvider);
    }

    @Test
    void routesAcceptedDriverToPickup() {
        Trip trip = acceptedTrip();
        when(tripRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.of(trip));
        when(routeGeometryProvider.route(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(routePlan());

        var response = service.route(20L, 99L, CURRENT_LOCATION);

        assertThat(response.destinationType()).isEqualTo(RouteDestinationType.PICKUP);
        assertThat(response.destination().lat()).isEqualByComparingTo("10.77");
        assertThat(response.destination().lng()).isEqualByComparingTo("106.7");
        assertThat(response.geometry().type()).isEqualTo("LineString");
        assertThat(response.geometry().coordinates()).hasSize(2);
        assertThat(response.steps()).singleElement()
                .satisfies(step -> assertThat(step.maneuverModifier()).isEqualTo("right"));
        assertRouteLocations("10.76", "106.69", "10.77", "106.7");
    }

    @Test
    void routesArrivedDriverToDropoff() {
        Trip trip = acceptedTrip();
        trip.markArrived();
        when(tripRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.of(trip));
        when(routeGeometryProvider.route(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(routePlan());

        var response = service.route(20L, 99L, CURRENT_LOCATION);

        assertThat(response.destinationType()).isEqualTo(RouteDestinationType.DROPOFF);
        assertThat(response.destination().lat()).isEqualByComparingTo("10.813");
        assertThat(response.destination().lng()).isEqualByComparingTo("106.665");
        assertRouteLocations("10.76", "106.69", "10.813", "106.665");
    }

    @Test
    void rejectsDriverThatIsNotAssignedToTrip() {
        Trip trip = acceptedTrip();
        when(tripRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> service.route(21L, 99L, CURRENT_LOCATION))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN)
                );

        verifyNoInteractions(routeGeometryProvider);
    }

    @Test
    void rejectsRoutingForSearchingTrip() {
        Trip trip = sampleTrip();
        when(tripRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> service.route(20L, 99L, CURRENT_LOCATION))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN)
                );

        verify(routeGeometryProvider, never()).route(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void rejectsRoutingAfterTripCompleted() {
        Trip trip = acceptedTrip();
        trip.markArrived();
        trip.startTrip();
        trip.complete(BigDecimal.valueOf(40000), BigDecimal.valueOf(5.2), 20);
        when(tripRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> service.route(20L, 99L, CURRENT_LOCATION))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.TRIP_ROUTE_NOT_AVAILABLE)
                );

        verifyNoInteractions(routeGeometryProvider);
    }

    private void assertRouteLocations(
            String originLat,
            String originLng,
            String destinationLat,
            String destinationLng
    ) {
        ArgumentCaptor<Location> originCaptor = ArgumentCaptor.forClass(Location.class);
        ArgumentCaptor<Location> destinationCaptor = ArgumentCaptor.forClass(Location.class);
        verify(routeGeometryProvider).route(originCaptor.capture(), destinationCaptor.capture());
        assertThat(originCaptor.getValue().latitude()).isEqualByComparingTo(originLat);
        assertThat(originCaptor.getValue().longitude()).isEqualByComparingTo(originLng);
        assertThat(destinationCaptor.getValue().latitude()).isEqualByComparingTo(destinationLat);
        assertThat(destinationCaptor.getValue().longitude()).isEqualByComparingTo(destinationLng);
    }

    private RoutePlan routePlan() {
        return new RoutePlan(
                2300,
                480,
                new RouteGeometry("LineString", List.of(
                        List.of(BigDecimal.valueOf(106.69), BigDecimal.valueOf(10.76)),
                        List.of(BigDecimal.valueOf(106.7), BigDecimal.valueOf(10.77))
                )),
                List.of(new RouteStep(
                        125,
                        32,
                        "Le Loi",
                        "turn",
                        "right",
                        BigDecimal.valueOf(106.695),
                        BigDecimal.valueOf(10.765)
                ))
        );
    }

    private Trip acceptedTrip() {
        Trip trip = sampleTrip();
        trip.accept(driver(20L));
        return trip;
    }

    private Trip sampleTrip() {
        PricingConfig pricingConfig = PricingConfig.create(
                VehicleType.MOTORBIKE,
                BigDecimal.valueOf(10000),
                BigDecimal.valueOf(4000),
                BigDecimal.valueOf(300),
                BigDecimal.valueOf(15000),
                BigDecimal.ONE,
                Instant.parse("2026-01-01T00:00:00Z")
        );
        Trip trip = Trip.create(
                passenger(10L),
                VehicleType.MOTORBIKE,
                PaymentMethod.CASH,
                "Ben Thanh Market",
                point(106.7000, 10.7700),
                "Tan Son Nhat Airport",
                point(106.6650, 10.8130),
                BigDecimal.valueOf(4.2),
                18,
                BigDecimal.valueOf(32000),
                pricingConfig
        );
        ReflectionTestUtils.setField(trip, "id", 99L);
        return trip;
    }

    private User passenger(Long id) {
        User user = User.create("Passenger", "0900000000", null, "hash", Set.of(UserRole.PASSENGER));
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private User driver(Long id) {
        User user = User.create("Driver", "0900000001", null, "hash", Set.of(UserRole.DRIVER));
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static org.locationtech.jts.geom.Point point(double longitude, double latitude) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
    }
}
