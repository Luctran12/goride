package com.example.goride.matching.service;

import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.domain.TripStatus;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.driver.domain.VehicleType;
import com.example.goride.matching.domain.DriverCandidate;
import com.example.goride.matching.domain.DriverOffer;
import com.example.goride.matching.domain.MatchingRequest;
import com.example.goride.matching.notification.DriverOfferNotification;
import com.example.goride.matching.notification.DriverOfferNotifier;
import com.example.goride.matching.telemetry.MatchingTelemetryTrigger;
import com.example.goride.user.domain.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchingTripMatchingServiceTests {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    @Mock
    private TripRepository tripRepository;

    @Mock
    private DriverCandidateStore candidateStore;

    @Mock
    private MatchingService matchingService;

    @Mock
    private DriverOfferNotifier driverOfferNotifier;

    @Test
    void driverAvailabilityMatchesSearchingTripsWithoutActiveOffer() {
        Trip trip = fullTrip();
        DriverOffer offer = offer();
        when(tripRepository.findByStatusAndDeletedAtIsNullOrderByRequestedAtAsc(TripStatus.SEARCHING))
                .thenReturn(List.of(trip));
        when(candidateStore.findTripMatching(99L)).thenReturn(Optional.empty());
        when(matchingService.findAndLockDriver(
                any(MatchingRequest.class),
                eq(MatchingTelemetryTrigger.RECOVERY)
        )).thenReturn(Optional.of(offer));

        service().matchOpenSearchingTrips(20L);

        ArgumentCaptor<MatchingRequest> requestCaptor = ArgumentCaptor.forClass(MatchingRequest.class);
        ArgumentCaptor<DriverOfferNotification> notificationCaptor =
                ArgumentCaptor.forClass(DriverOfferNotification.class);
        verify(matchingService).findAndLockDriver(
                requestCaptor.capture(),
                eq(MatchingTelemetryTrigger.RECOVERY)
        );
        verify(driverOfferNotifier).notifyDriver(org.mockito.Mockito.eq(20L), notificationCaptor.capture());
        assertThat(requestCaptor.getValue().tripId()).isEqualTo(99L);
        assertThat(requestCaptor.getValue().vehicleType()).isEqualTo(VehicleType.MOTORBIKE);
        assertThat(notificationCaptor.getValue().tripId()).isEqualTo(99L);
    }

    @Test
    void driverAvailabilitySkipsTripsWithActiveOffer() {
        Trip trip = tripWithIdOnly();
        when(tripRepository.findByStatusAndDeletedAtIsNullOrderByRequestedAtAsc(TripStatus.SEARCHING))
                .thenReturn(List.of(trip));
        when(candidateStore.findTripMatching(99L)).thenReturn(Optional.of(
                new com.example.goride.matching.domain.TripMatchingState(
                        99L,
                        20L,
                        1,
                        Instant.parse("2026-06-16T04:00:00Z"),
                        java.util.Set.of()
                )
        ));

        service().matchOpenSearchingTrips(20L);

        verify(matchingService, never()).findAndLockDriver(
                any(),
                any(MatchingTelemetryTrigger.class)
        );
        verify(driverOfferNotifier, never()).notifyDriver(any(), any());
    }

    private SearchingTripMatchingService service() {
        return new SearchingTripMatchingService(
                tripRepository,
                candidateStore,
                matchingService,
                driverOfferNotifier
        );
    }

    private Trip fullTrip() {
        Trip trip = org.mockito.Mockito.mock(Trip.class);
        User passenger = org.mockito.Mockito.mock(User.class);
        when(trip.getId()).thenReturn(99L);
        when(trip.getPassenger()).thenReturn(passenger);
        when(passenger.getId()).thenReturn(10L);
        when(trip.getVehicleType()).thenReturn(VehicleType.MOTORBIKE);
        when(trip.getPickupLocation()).thenReturn(GEOMETRY_FACTORY.createPoint(new Coordinate(106.7009, 10.7769)));
        when(trip.getDropoffLocation()).thenReturn(GEOMETRY_FACTORY.createPoint(new Coordinate(106.6800, 10.7850)));
        when(trip.getEstimatedFare()).thenReturn(BigDecimal.valueOf(38000));
        return trip;
    }

    private Trip tripWithIdOnly() {
        Trip trip = org.mockito.Mockito.mock(Trip.class);
        when(trip.getId()).thenReturn(99L);
        return trip;
    }

    private DriverOffer offer() {
        DriverCandidate candidate = new DriverCandidate(
                20L,
                350L,
                VehicleType.MOTORBIKE,
                BigDecimal.valueOf(4.9),
                "Driver",
                null
        );
        return new DriverOffer(99L, candidate, 1, Instant.parse("2026-06-16T04:00:00Z"));
    }
}
