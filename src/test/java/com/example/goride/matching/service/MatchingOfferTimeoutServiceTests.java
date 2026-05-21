package com.example.goride.matching.service;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.booking.domain.PricingConfig;
import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.domain.TripStatus;
import com.example.goride.booking.domain.TripStatusHistory;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.booking.repository.TripStatusHistoryRepository;
import com.example.goride.driver.domain.VehicleType;
import com.example.goride.matching.domain.DriverCandidate;
import com.example.goride.matching.domain.DriverOffer;
import com.example.goride.matching.domain.MatchingRequest;
import com.example.goride.matching.domain.TripMatchingState;
import com.example.goride.matching.notification.DriverOfferNotification;
import com.example.goride.matching.notification.DriverOfferNotifier;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchingOfferTimeoutServiceTests {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Mock
    private TripRepository tripRepository;

    @Mock
    private TripStatusHistoryRepository tripStatusHistoryRepository;

    @Mock
    private DriverCandidateStore candidateStore;

    @Mock
    private MatchingService matchingService;

    @Mock
    private DriverOfferNotifier driverOfferNotifier;

    private MatchingOfferTimeoutService timeoutService;

    @BeforeEach
    void setUp() {
        timeoutService = new MatchingOfferTimeoutService(
                tripRepository,
                tripStatusHistoryRepository,
                candidateStore,
                matchingService,
                driverOfferNotifier
        );
    }

    @Test
    void cleansActiveTripWhenMatchingStateIsMissing() {
        when(candidateStore.findTripMatching(99L)).thenReturn(Optional.empty());

        boolean processed = timeoutService.processExpiredOffer(99L);

        assertThat(processed).isFalse();
        verify(candidateStore).clearTripMatching(99L);
        verifyNoInteractions(tripRepository, matchingService, driverOfferNotifier);
    }

    @Test
    void ignoresOfferThatHasNotExpired() {
        when(candidateStore.findTripMatching(99L)).thenReturn(Optional.of(state(20L, 1, Instant.now().plusSeconds(30), Set.of())));

        boolean processed = timeoutService.processExpiredOffer(99L);

        assertThat(processed).isFalse();
        verifyNoInteractions(tripRepository, matchingService, driverOfferNotifier);
    }

    @Test
    void expiredOfferRetriesNextDriverAndSendsNextOffer() {
        Trip trip = sampleTrip();
        DriverOffer nextOffer = new DriverOffer(
                99L,
                candidate(21L, 450L),
                2,
                Instant.now().plusSeconds(30)
        );
        when(candidateStore.findTripMatching(99L)).thenReturn(Optional.of(state(20L, 1, Instant.now().minusSeconds(1), Set.of())));
        when(tripRepository.findActiveByIdForUpdate(99L)).thenReturn(Optional.of(trip));
        when(matchingService.findAndLockDriver(any(MatchingRequest.class), eq(2), any()))
                .thenReturn(Optional.of(nextOffer));

        boolean processed = timeoutService.processExpiredOffer(99L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<Long>> rejectedCaptor = ArgumentCaptor.forClass(Set.class);
        ArgumentCaptor<DriverOfferNotification> notificationCaptor =
                ArgumentCaptor.forClass(DriverOfferNotification.class);
        verify(candidateStore).releaseCandidateLock(20L);
        verify(candidateStore).clearTripMatching(99L);
        verify(matchingService).findAndLockDriver(any(MatchingRequest.class), eq(2), rejectedCaptor.capture());
        verify(driverOfferNotifier).notifyDriver(eq(21L), notificationCaptor.capture());
        verify(tripRepository, never()).save(any());
        assertThat(processed).isTrue();
        assertThat(rejectedCaptor.getValue()).containsExactly(20L);
        assertThat(notificationCaptor.getValue().tripId()).isEqualTo(99L);
        assertThat(notificationCaptor.getValue().distanceMeters()).isEqualTo(450L);
    }

    @Test
    void expiredOfferMarksTripNoDriverAfterThirdAttempt() {
        Trip trip = sampleTrip();
        when(candidateStore.findTripMatching(99L))
                .thenReturn(Optional.of(state(20L, 3, Instant.now().minusSeconds(1), Set.of(18L, 19L))));
        when(tripRepository.findActiveByIdForUpdate(99L)).thenReturn(Optional.of(trip));
        when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean processed = timeoutService.processExpiredOffer(99L);

        ArgumentCaptor<TripStatusHistory> historyCaptor = ArgumentCaptor.forClass(TripStatusHistory.class);
        verify(matchingService, never()).findAndLockDriver(any(), anyInt(), any());
        verify(tripStatusHistoryRepository).save(historyCaptor.capture());
        assertThat(processed).isTrue();
        assertThat(trip.getStatus()).isEqualTo(TripStatus.NO_DRIVER);
        assertThat(historyCaptor.getValue().getToStatus()).isEqualTo(TripStatus.NO_DRIVER);
        assertThat(historyCaptor.getValue().getNote()).isEqualTo("Matching exhausted after offer timeout");
    }

    @Test
    void expiredOfferMarksTripNoDriverWhenNoNextDriverCanBeLocked() {
        Trip trip = sampleTrip();
        when(candidateStore.findTripMatching(99L)).thenReturn(Optional.of(state(20L, 1, Instant.now().minusSeconds(1), Set.of())));
        when(tripRepository.findActiveByIdForUpdate(99L)).thenReturn(Optional.of(trip));
        when(matchingService.findAndLockDriver(any(MatchingRequest.class), eq(2), any()))
                .thenReturn(Optional.empty());
        when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean processed = timeoutService.processExpiredOffer(99L);

        verify(driverOfferNotifier, never()).notifyDriver(any(), any());
        assertThat(processed).isTrue();
        assertThat(trip.getStatus()).isEqualTo(TripStatus.NO_DRIVER);
    }

    @Test
    void expiredOfferCleansStateWhenTripIsNoLongerSearching() {
        Trip trip = sampleTrip();
        trip.cancel("Passenger cancelled");
        when(candidateStore.findTripMatching(99L)).thenReturn(Optional.of(state(20L, 1, Instant.now().minusSeconds(1), Set.of())));
        when(tripRepository.findActiveByIdForUpdate(99L)).thenReturn(Optional.of(trip));

        boolean processed = timeoutService.processExpiredOffer(99L);

        verify(candidateStore).releaseCandidateLock(20L);
        verify(candidateStore).clearTripMatching(99L);
        verifyNoInteractions(matchingService, driverOfferNotifier);
        assertThat(processed).isTrue();
    }

    private TripMatchingState state(Long driverId, int attempt, Instant expiresAt, Set<Long> rejectedDriverIds) {
        return new TripMatchingState(99L, driverId, attempt, expiresAt, rejectedDriverIds);
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
        User passenger = User.create("Passenger", "0900000000", null, "hash", Set.of(UserRole.PASSENGER));
        ReflectionTestUtils.setField(passenger, "id", 10L);
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

    private static org.locationtech.jts.geom.Point point(double longitude, double latitude) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
    }
}
