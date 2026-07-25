package com.example.goride.matching.service;

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
import com.example.goride.matching.domain.DriverCandidate;
import com.example.goride.matching.domain.DriverOffer;
import com.example.goride.matching.domain.DriverOfferDecision;
import com.example.goride.matching.domain.MatchingRequest;
import com.example.goride.matching.domain.TripMatchingState;
import com.example.goride.matching.notification.DriverOfferNotification;
import com.example.goride.matching.notification.DriverOfferNotifier;
import com.example.goride.notification.dto.TripStatusNotification;
import com.example.goride.notification.dto.UserNotification;
import com.example.goride.notification.service.TripRealtimeNotifier;
import com.example.goride.tracking.service.TripDriverLocationBootstrapService;
import com.example.goride.user.domain.User;
import com.example.goride.user.domain.UserRole;
import com.example.goride.user.repository.UserRepository;
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
class DriverOfferResponseServiceTests {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Mock
    private TripRepository tripRepository;

    @Mock
    private TripStatusHistoryRepository tripStatusHistoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DriverCandidateStore candidateStore;

    @Mock
    private MatchingService matchingService;

    @Mock
    private DriverOfferNotifier driverOfferNotifier;

    @Mock
    private TripRealtimeNotifier tripRealtimeNotifier;

    @Mock
    private TripDriverLocationBootstrapService tripDriverLocationBootstrapService;

    private DriverOfferResponseService service;

    @BeforeEach
    void setUp() {
        service = new DriverOfferResponseService(
                tripRepository,
                tripStatusHistoryRepository,
                userRepository,
                candidateStore,
                matchingService,
                driverOfferNotifier,
                tripRealtimeNotifier,
                tripDriverLocationBootstrapService
        );
    }

    @Test
    void acceptOfferAssignsDriverAndMarksDriverBusy() {
        Trip trip = sampleTrip();
        User driver = driver(20L);
        when(candidateStore.findTripMatching(99L)).thenReturn(Optional.of(state(20L, 1, Set.of())));
        when(tripRepository.findActiveByIdForUpdate(99L)).thenReturn(Optional.of(trip));
        when(userRepository.findByIdAndDeletedAtIsNull(20L)).thenReturn(Optional.of(driver));
        when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.respondToOffer(20L, 99L, DriverOfferDecision.ACCEPT);

        ArgumentCaptor<TripStatusHistory> historyCaptor = ArgumentCaptor.forClass(TripStatusHistory.class);
        verify(tripStatusHistoryRepository).save(historyCaptor.capture());
        verify(candidateStore).markCandidateBusy(20L);
        verify(candidateStore).releaseCandidateLock(20L);
        verify(candidateStore).clearTripMatching(99L);
        verify(tripDriverLocationBootstrapService).bootstrap(99L, 20L);
        verifyPassengerNotification(TripStatus.ACCEPTED);
        assertThat(response.tripId()).isEqualTo(99L);
        assertThat(response.status()).isEqualTo(TripStatus.ACCEPTED);
        assertThat(trip.getDriver()).isSameAs(driver);
        assertThat(historyCaptor.getValue().getFromStatus()).isEqualTo(TripStatus.SEARCHING);
        assertThat(historyCaptor.getValue().getToStatus()).isEqualTo(TripStatus.ACCEPTED);
        assertThat(historyCaptor.getValue().getChangedBy()).isSameAs(driver);
    }

    @Test
    void rejectOfferRetriesNextDriverAndSendsNextOffer() {
        Trip trip = sampleTrip();
        DriverOffer nextOffer = new DriverOffer(
                99L,
                candidate(21L, 450L),
                2,
                Instant.now().plusSeconds(30)
        );
        when(candidateStore.findTripMatching(99L)).thenReturn(Optional.of(state(20L, 1, Set.of())));
        when(tripRepository.findActiveByIdForUpdate(99L)).thenReturn(Optional.of(trip));
        when(matchingService.findAndLockDriver(any(MatchingRequest.class), eq(2), any()))
                .thenReturn(Optional.of(nextOffer));

        var response = service.respondToOffer(20L, 99L, DriverOfferDecision.REJECT);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<Long>> rejectedCaptor = ArgumentCaptor.forClass(Set.class);
        ArgumentCaptor<DriverOfferNotification> notificationCaptor =
                ArgumentCaptor.forClass(DriverOfferNotification.class);
        verify(candidateStore).releaseCandidateLock(20L);
        verify(candidateStore).clearTripMatching(99L);
        verify(matchingService).findAndLockDriver(any(MatchingRequest.class), eq(2), rejectedCaptor.capture());
        verify(driverOfferNotifier).notifyDriver(eq(21L), notificationCaptor.capture());
        verify(tripRepository, never()).save(any());
        assertThat(response.tripId()).isEqualTo(99L);
        assertThat(response.status()).isEqualTo(TripStatus.SEARCHING);
        assertThat(rejectedCaptor.getValue()).containsExactly(20L);
        assertThat(notificationCaptor.getValue().tripId()).isEqualTo(99L);
        assertThat(notificationCaptor.getValue().distanceMeters()).isEqualTo(450L);
    }

    @Test
    void rejectOfferKeepsSearchingAfterThirdAttemptWhenNoNextDriverCanBeLocked() {
        Trip trip = sampleTrip();
        when(candidateStore.findTripMatching(99L)).thenReturn(Optional.of(state(20L, 3, Set.of(18L, 19L))));
        when(tripRepository.findActiveByIdForUpdate(99L)).thenReturn(Optional.of(trip));
        when(matchingService.findAndLockDriver(any(MatchingRequest.class), eq(4), any()))
                .thenReturn(Optional.empty());

        var response = service.respondToOffer(20L, 99L, DriverOfferDecision.REJECT);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<Long>> rejectedCaptor = ArgumentCaptor.forClass(Set.class);
        verify(matchingService).findAndLockDriver(any(MatchingRequest.class), eq(4), rejectedCaptor.capture());
        verify(tripRepository, never()).save(any());
        verify(tripStatusHistoryRepository, never()).save(any());
        verify(tripRealtimeNotifier, never()).notifyPassenger(any(), any());
        verify(tripRealtimeNotifier, never()).broadcastTripStatus(any(), any());
        verifyNoInteractions(tripDriverLocationBootstrapService);
        assertThat(response.status()).isEqualTo(TripStatus.SEARCHING);
        assertThat(trip.getStatus()).isEqualTo(TripStatus.SEARCHING);
        assertThat(rejectedCaptor.getValue()).containsExactlyInAnyOrder(18L, 19L, 20L);
    }

    @Test
    void rejectOfferKeepsTripSearchingWhenNoNextDriverCanBeLocked() {
        Trip trip = sampleTrip();
        when(candidateStore.findTripMatching(99L)).thenReturn(Optional.of(state(20L, 1, Set.of())));
        when(tripRepository.findActiveByIdForUpdate(99L)).thenReturn(Optional.of(trip));
        when(matchingService.findAndLockDriver(any(MatchingRequest.class), eq(2), any()))
                .thenReturn(Optional.empty());

        var response = service.respondToOffer(20L, 99L, DriverOfferDecision.REJECT);

        verify(driverOfferNotifier, never()).notifyDriver(any(), any());
        verify(tripRepository, never()).save(any());
        verify(tripStatusHistoryRepository, never()).save(any());
        verify(tripRealtimeNotifier, never()).notifyPassenger(any(), any());
        verify(tripRealtimeNotifier, never()).broadcastTripStatus(any(), any());
        verifyNoInteractions(tripDriverLocationBootstrapService);
        assertThat(response.status()).isEqualTo(TripStatus.SEARCHING);
        assertThat(trip.getStatus()).isEqualTo(TripStatus.SEARCHING);
    }

    @Test
    void respondRejectsDriverThatDoesNotOwnOffer() {
        when(candidateStore.findTripMatching(99L)).thenReturn(Optional.of(state(20L, 1, Set.of())));

        assertThatThrownBy(() -> service.respondToOffer(21L, 99L, DriverOfferDecision.ACCEPT))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN)
                );
        verifyNoInteractions(tripRepository);
    }

    @Test
    void respondRejectsExpiredOfferAndCleansMatchingState() {
        when(candidateStore.findTripMatching(99L)).thenReturn(Optional.of(new TripMatchingState(
                99L,
                20L,
                1,
                Instant.now().minusSeconds(1),
                Set.of()
        )));

        assertThatThrownBy(() -> service.respondToOffer(20L, 99L, DriverOfferDecision.ACCEPT))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.MATCHING_OFFER_EXPIRED)
                );
        verify(candidateStore).releaseCandidateLock(20L);
        verify(candidateStore).clearTripMatching(99L);
        verifyNoInteractions(tripRepository);
    }

    private void verifyPassengerNotification(TripStatus status) {
        ArgumentCaptor<UserNotification> notificationCaptor = ArgumentCaptor.forClass(UserNotification.class);
        ArgumentCaptor<TripStatusNotification> statusCaptor = ArgumentCaptor.forClass(TripStatusNotification.class);
        verify(tripRealtimeNotifier).notifyPassenger(eq(10L), notificationCaptor.capture());
        verify(tripRealtimeNotifier).broadcastTripStatus(eq(99L), statusCaptor.capture());
        assertThat(notificationCaptor.getValue().data())
                .containsEntry("tripId", 99L)
                .containsEntry("status", status.name());
        assertThat(statusCaptor.getValue().tripId()).isEqualTo(99L);
        assertThat(statusCaptor.getValue().status()).isEqualTo(status);
    }

    private TripMatchingState state(Long driverId, int attempt, Set<Long> rejectedDriverIds) {
        return new TripMatchingState(
                99L,
                driverId,
                attempt,
                Instant.now().plusSeconds(30),
                rejectedDriverIds
        );
    }

    private DriverCandidate candidate(Long driverId, long distanceMeters) {
        return new DriverCandidate(
                driverId,
                distanceMeters,
                VehicleType.MOTORBIKE,
                BigDecimal.valueOf(5.0),
                "Driver " + driverId,
                null
        );
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
