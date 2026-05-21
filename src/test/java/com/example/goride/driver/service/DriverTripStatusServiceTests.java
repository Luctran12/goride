package com.example.goride.driver.service;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.booking.domain.PricingConfig;
import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.domain.TripStatus;
import com.example.goride.booking.domain.TripStatusHistory;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.booking.repository.TripStatusHistoryRepository;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.driver.domain.VehicleType;
import com.example.goride.notification.domain.NotificationType;
import com.example.goride.notification.dto.TripStatusNotification;
import com.example.goride.notification.dto.UserNotification;
import com.example.goride.notification.service.TripRealtimeNotifier;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverTripStatusServiceTests {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Mock
    private TripRepository tripRepository;

    @Mock
    private TripStatusHistoryRepository tripStatusHistoryRepository;

    @Mock
    private TripRealtimeNotifier tripRealtimeNotifier;

    private DriverTripStatusService service;

    @BeforeEach
    void setUp() {
        service = new DriverTripStatusService(
                tripRepository,
                tripStatusHistoryRepository,
                tripRealtimeNotifier
        );
    }

    @Test
    void marksAcceptedTripArrived() {
        User driver = driver(20L);
        Trip trip = acceptedTrip(driver);
        stubTripForUpdate(trip);

        var response = service.updateTripStatus(20L, 99L, TripStatus.ARRIVED);

        TripStatusHistory history = verifyHistory(TripStatus.ACCEPTED, TripStatus.ARRIVED, driver);
        assertThat(response.tripId()).isEqualTo(99L);
        assertThat(response.status()).isEqualTo(TripStatus.ARRIVED);
        assertThat(trip.getArrivedAt()).isNotNull();
        assertThat(history.getNote()).isEqualTo("Driver updated trip status");
        verifyPassengerNotification(NotificationType.DRIVER_ARRIVED, TripStatus.ARRIVED);
    }

    @Test
    void startsArrivedTrip() {
        User driver = driver(20L);
        Trip trip = acceptedTrip(driver);
        trip.markArrived();
        stubTripForUpdate(trip);

        var response = service.updateTripStatus(20L, 99L, TripStatus.IN_PROGRESS);

        verifyHistory(TripStatus.ARRIVED, TripStatus.IN_PROGRESS, driver);
        assertThat(response.status()).isEqualTo(TripStatus.IN_PROGRESS);
        assertThat(trip.getStartedAt()).isNotNull();
        verifyPassengerNotification(NotificationType.TRIP_STARTED, TripStatus.IN_PROGRESS);
    }

    @Test
    void completesInProgressTripWithEstimatedFareUntilPaymentIsImplemented() {
        User driver = driver(20L);
        Trip trip = acceptedTrip(driver);
        trip.markArrived();
        trip.startTrip();
        stubTripForUpdate(trip);

        var response = service.updateTripStatus(20L, 99L, TripStatus.COMPLETED);

        verifyHistory(TripStatus.IN_PROGRESS, TripStatus.COMPLETED, driver);
        assertThat(response.status()).isEqualTo(TripStatus.COMPLETED);
        assertThat(trip.getFinalFare()).isEqualByComparingTo(BigDecimal.valueOf(32000));
        assertThat(trip.getActualDistanceKm()).isEqualByComparingTo(BigDecimal.valueOf(4.2));
        assertThat(trip.getActualDurationMin()).isEqualTo(18);
        assertThat(trip.getCompletedAt()).isNotNull();
        verifyPassengerNotification(NotificationType.TRIP_COMPLETED, TripStatus.COMPLETED);
        verifyDriverCompletionNotification();
    }

    @Test
    void rejectsDriverThatDoesNotOwnTrip() {
        Trip trip = acceptedTrip(driver(20L));
        when(tripRepository.findActiveByIdForUpdate(99L)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> service.updateTripStatus(21L, 99L, TripStatus.ARRIVED))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN)
                );

        verify(tripRepository, never()).save(any());
        verifyNoInteractions(tripStatusHistoryRepository, tripRealtimeNotifier);
    }

    @Test
    void rejectsInvalidTransition() {
        Trip trip = acceptedTrip(driver(20L));
        when(tripRepository.findActiveByIdForUpdate(99L)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> service.updateTripStatus(20L, 99L, TripStatus.COMPLETED))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.TRIP_STATUS_INVALID_TRANSITION)
                );

        verify(tripRepository, never()).save(any());
        verifyNoInteractions(tripStatusHistoryRepository, tripRealtimeNotifier);
    }

    @Test
    void rejectsUnsupportedDriverStatus() {
        Trip trip = acceptedTrip(driver(20L));
        when(tripRepository.findActiveByIdForUpdate(99L)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> service.updateTripStatus(20L, 99L, TripStatus.CANCELLED))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.TRIP_STATUS_INVALID_TRANSITION)
                );

        verify(tripRepository, never()).save(any());
        verifyNoInteractions(tripStatusHistoryRepository, tripRealtimeNotifier);
    }

    @Test
    void rejectsMissingStatus() {
        assertThatThrownBy(() -> service.updateTripStatus(20L, 99L, null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR)
                );

        verifyNoInteractions(tripRepository, tripStatusHistoryRepository, tripRealtimeNotifier);
    }

    private void stubTripForUpdate(Trip trip) {
        when(tripRepository.findActiveByIdForUpdate(99L)).thenReturn(Optional.of(trip));
        when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private TripStatusHistory verifyHistory(TripStatus fromStatus, TripStatus toStatus, User driver) {
        ArgumentCaptor<TripStatusHistory> historyCaptor = ArgumentCaptor.forClass(TripStatusHistory.class);
        verify(tripStatusHistoryRepository).save(historyCaptor.capture());
        TripStatusHistory history = historyCaptor.getValue();
        assertThat(history.getTrip().getId()).isEqualTo(99L);
        assertThat(history.getFromStatus()).isEqualTo(fromStatus);
        assertThat(history.getToStatus()).isEqualTo(toStatus);
        assertThat(history.getChangedBy()).isSameAs(driver);
        return history;
    }

    private void verifyPassengerNotification(NotificationType type, TripStatus status) {
        ArgumentCaptor<UserNotification> notificationCaptor = ArgumentCaptor.forClass(UserNotification.class);
        ArgumentCaptor<TripStatusNotification> statusCaptor = ArgumentCaptor.forClass(TripStatusNotification.class);
        verify(tripRealtimeNotifier).notifyPassenger(eq(10L), notificationCaptor.capture());
        verify(tripRealtimeNotifier).broadcastTripStatus(eq(99L), statusCaptor.capture());
        assertThat(notificationCaptor.getValue().type()).isEqualTo(type);
        assertThat(notificationCaptor.getValue().data())
                .containsEntry("tripId", 99L)
                .containsEntry("status", status.name())
                .containsEntry("driverId", 20L);
        assertThat(statusCaptor.getValue().tripId()).isEqualTo(99L);
        assertThat(statusCaptor.getValue().status()).isEqualTo(status);
    }

    private void verifyDriverCompletionNotification() {
        ArgumentCaptor<UserNotification> notificationCaptor = ArgumentCaptor.forClass(UserNotification.class);
        verify(tripRealtimeNotifier).notifyUser(eq(20L), notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().type()).isEqualTo(NotificationType.TRIP_COMPLETED);
        assertThat(notificationCaptor.getValue().data())
                .containsEntry("tripId", 99L)
                .containsEntry("status", TripStatus.COMPLETED.name())
                .containsEntry("driverId", 20L)
                .containsEntry("finalFare", BigDecimal.valueOf(32000));
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
