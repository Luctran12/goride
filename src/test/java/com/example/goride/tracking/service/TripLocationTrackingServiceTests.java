package com.example.goride.tracking.service;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.booking.domain.PricingConfig;
import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.domain.TripStatus;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.driver.domain.VehicleType;
import com.example.goride.tracking.domain.TripLocationHistory;
import com.example.goride.tracking.dto.DriverLocationUpdateRequest;
import com.example.goride.tracking.repository.TripLocationHistoryRepository;
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
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripLocationTrackingServiceTests {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Mock
    private TripRepository tripRepository;

    @Mock
    private TripLocationHistoryRepository tripLocationHistoryRepository;

    @Mock
    private LatestDriverLocationStore latestDriverLocationStore;

    @Mock
    private TripLocationNotifier tripLocationNotifier;

    private TripLocationTrackingService service;

    @BeforeEach
    void setUp() {
        service = new TripLocationTrackingService(
                tripRepository,
                tripLocationHistoryRepository,
                latestDriverLocationStore,
                tripLocationNotifier
        );
    }

    @Test
    void recordsCachesAndBroadcastsDriverLocationForInProgressTrip() {
        Trip trip = inProgressTrip(driver(20L));
        DriverLocationUpdateRequest request = locationRequest();
        when(tripRepository.findFirstByDriverIdAndStatusInAndDeletedAtIsNullOrderByAcceptedAtDesc(
                eq(20L),
                any()
        )).thenReturn(Optional.of(trip));
        when(tripLocationHistoryRepository.save(any(TripLocationHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateDriverLocation(20L, request);

        ArgumentCaptor<TripLocationHistory> historyCaptor = ArgumentCaptor.forClass(TripLocationHistory.class);
        ArgumentCaptor<LatestDriverLocationStore.LatestDriverLocation> latestCaptor =
                ArgumentCaptor.forClass(LatestDriverLocationStore.LatestDriverLocation.class);
        verify(tripLocationHistoryRepository).save(historyCaptor.capture());
        verify(latestDriverLocationStore).save(latestCaptor.capture());
        verify(tripLocationNotifier).broadcastDriverLocation(99L, response);
        assertThat(response.tripId()).isEqualTo(99L);
        assertThat(response.driverId()).isEqualTo(20L);
        assertThat(response.lat()).isEqualByComparingTo("10.7800");
        assertThat(response.lng()).isEqualByComparingTo("106.6900");
        assertThat(response.bearing()).isEqualByComparingTo("92.5");
        assertThat(response.speed()).isEqualByComparingTo("28.4");
        assertThat(response.updatedAt()).isNotNull();
        assertThat(historyCaptor.getValue().getTrip()).isSameAs(trip);
        assertThat(historyCaptor.getValue().getLocation().getY()).isEqualTo(10.78);
        assertThat(historyCaptor.getValue().getLocation().getX()).isEqualTo(106.69);
        assertThat(latestCaptor.getValue().tripId()).isEqualTo(99L);
        assertThat(latestCaptor.getValue().driverId()).isEqualTo(20L);
    }

    @Test
    void cachesAndBroadcastsDriverLocationForAcceptedTripWithoutRecordingHistory() {
        Trip trip = acceptedTrip(driver(20L));
        DriverLocationUpdateRequest request = locationRequest();
        when(tripRepository.findFirstByDriverIdAndStatusInAndDeletedAtIsNullOrderByAcceptedAtDesc(
                eq(20L),
                any()
        )).thenReturn(Optional.of(trip));

        var response = service.updateDriverLocation(20L, request);

        ArgumentCaptor<LatestDriverLocationStore.LatestDriverLocation> latestCaptor =
                ArgumentCaptor.forClass(LatestDriverLocationStore.LatestDriverLocation.class);
        verifyNoInteractions(tripLocationHistoryRepository);
        verify(latestDriverLocationStore).save(latestCaptor.capture());
        verify(tripLocationNotifier).broadcastDriverLocation(99L, response);
        assertThat(response.tripId()).isEqualTo(99L);
        assertThat(response.driverId()).isEqualTo(20L);
        assertThat(response.lat()).isEqualByComparingTo("10.7800");
        assertThat(response.lng()).isEqualByComparingTo("106.6900");
        assertThat(response.updatedAt()).isNotNull();
        assertThat(latestCaptor.getValue().tripId()).isEqualTo(99L);
    }

    @Test
    void rejectsLocationUpdateWhenDriverHasNoActiveAssignedTrip() {
        when(tripRepository.findFirstByDriverIdAndStatusInAndDeletedAtIsNullOrderByAcceptedAtDesc(
                eq(20L),
                any()
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateDriverLocation(20L, locationRequest()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.TRIP_NOT_FOUND)
                );

        verifyNoInteractions(tripLocationHistoryRepository, latestDriverLocationStore, tripLocationNotifier);
    }

    @Test
    void rejectsMissingLocationRequest() {
        assertThatThrownBy(() -> service.updateDriverLocation(20L, null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR)
                );

        verifyNoInteractions(tripRepository, tripLocationHistoryRepository, latestDriverLocationStore, tripLocationNotifier);
    }

    @Test
    void returnsLatestDriverLocationForTripPassenger() {
        Trip trip = inProgressTrip(driver(20L));
        Instant updatedAt = Instant.parse("2026-05-21T08:00:00Z");
        when(tripRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.of(trip));
        when(latestDriverLocationStore.findByDriverId(20L)).thenReturn(Optional.of(
                new LatestDriverLocationStore.LatestDriverLocation(
                        99L,
                        20L,
                        BigDecimal.valueOf(10.78),
                        BigDecimal.valueOf(106.69),
                        BigDecimal.valueOf(92.5),
                        BigDecimal.valueOf(28.4),
                        updatedAt
                )
        ));

        var response = service.getLatestDriverLocation(10L, 99L);

        assertThat(response.tripId()).isEqualTo(99L);
        assertThat(response.driverId()).isEqualTo(20L);
        assertThat(response.lat()).isEqualByComparingTo("10.78");
        assertThat(response.lng()).isEqualByComparingTo("106.69");
        assertThat(response.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void rejectsLatestLocationForDifferentPassenger() {
        Trip trip = inProgressTrip(driver(20L));
        when(tripRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> service.getLatestDriverLocation(11L, 99L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN)
                );

        verifyNoInteractions(latestDriverLocationStore);
    }

    @Test
    void returnsNotFoundWhenLatestLocationIsMissing() {
        Trip trip = inProgressTrip(driver(20L));
        when(tripRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.of(trip));
        when(latestDriverLocationStore.findByDriverId(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getLatestDriverLocation(10L, 99L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.DRIVER_LOCATION_NOT_FOUND)
                );
    }

    private DriverLocationUpdateRequest locationRequest() {
        return new DriverLocationUpdateRequest(
                BigDecimal.valueOf(10.7800),
                BigDecimal.valueOf(106.6900),
                BigDecimal.valueOf(92.5),
                BigDecimal.valueOf(28.4)
        );
    }

    private Trip inProgressTrip(User driver) {
        Trip trip = acceptedTrip(driver);
        trip.markArrived();
        trip.startTrip();
        return trip;
    }

    private Trip acceptedTrip(User driver) {
        Trip trip = sampleTrip();
        trip.accept(driver);
        return trip;
    }

    private Trip sampleTrip() {
        User passenger = passenger(10L);
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
                passenger,
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
