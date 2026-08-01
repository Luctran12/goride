package com.example.goride.matching.service;

import com.example.goride.booking.event.BookingCreatedEvent;
import com.example.goride.booking.event.BookingMatchingTrigger;
import com.example.goride.driver.domain.VehicleType;
import com.example.goride.matching.domain.DriverCandidate;
import com.example.goride.matching.domain.DriverOffer;
import com.example.goride.matching.domain.MatchingRequest;
import com.example.goride.matching.domain.TripMatchingState;
import com.example.goride.matching.notification.DriverOfferNotification;
import com.example.goride.matching.notification.DriverOfferNotifier;
import com.example.goride.matching.telemetry.MatchingTelemetryTrigger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingCreatedMatchingListenerTests {
    @Mock
    private MatchingService matchingService;

    @Mock
    private DriverOfferNotifier driverOfferNotifier;

    @Mock
    private DriverCandidateStore candidateStore;

    @Test
    void bookingCreatedTriggersMatchingAndSendsDriverOffer() {
        BookingCreatedEvent event = event();
        DriverOffer offer = offer();
        when(matchingService.findAndLockDriver(
                any(MatchingRequest.class),
                eq(MatchingTelemetryTrigger.BOOKING_CREATED)
        )).thenReturn(Optional.of(offer));
        BookingCreatedMatchingListener listener = new BookingCreatedMatchingListener(
                matchingService,
                candidateStore,
                driverOfferNotifier
        );

        listener.onBookingCreated(event);

        ArgumentCaptor<MatchingRequest> requestCaptor = ArgumentCaptor.forClass(MatchingRequest.class);
        ArgumentCaptor<DriverOfferNotification> notificationCaptor =
                ArgumentCaptor.forClass(DriverOfferNotification.class);
        verify(matchingService).findAndLockDriver(
                requestCaptor.capture(),
                eq(MatchingTelemetryTrigger.BOOKING_CREATED)
        );
        verify(driverOfferNotifier).notifyDriver(org.mockito.Mockito.eq(20L), notificationCaptor.capture());
        assertThat(requestCaptor.getValue().tripId()).isEqualTo(99L);
        assertThat(requestCaptor.getValue().vehicleType()).isEqualTo(VehicleType.MOTORBIKE);
        assertThat(notificationCaptor.getValue().tripId()).isEqualTo(99L);
        assertThat(notificationCaptor.getValue().passengerId()).isEqualTo(10L);
        assertThat(notificationCaptor.getValue().distanceMeters()).isEqualTo(350L);
        assertThat(notificationCaptor.getValue().expiresAt()).isEqualTo(offer.offerExpiresAt());
    }

    @Test
    void bookingCreatedKeepsSearchingWhenNoDriverCanBeLocked() {
        BookingCreatedEvent event = event();
        when(matchingService.findAndLockDriver(
                any(MatchingRequest.class),
                eq(MatchingTelemetryTrigger.BOOKING_CREATED)
        )).thenReturn(Optional.empty());
        BookingCreatedMatchingListener listener = new BookingCreatedMatchingListener(
                matchingService,
                candidateStore,
                driverOfferNotifier
        );

        listener.onBookingCreated(event);

        verify(driverOfferNotifier, never()).notifyDriver(any(), any());
    }

    @Test
    void scheduledDispatchUsesDedicatedTelemetryTrigger() {
        BookingCreatedEvent event = event(BookingMatchingTrigger.SCHEDULED_DISPATCH);
        when(matchingService.findAndLockDriver(
                any(MatchingRequest.class),
                eq(MatchingTelemetryTrigger.SCHEDULED_DISPATCH)
        )).thenReturn(Optional.empty());
        BookingCreatedMatchingListener listener = new BookingCreatedMatchingListener(
                matchingService,
                candidateStore,
                driverOfferNotifier
        );

        listener.onBookingCreated(event);

        verify(matchingService).findAndLockDriver(
                any(MatchingRequest.class),
                eq(MatchingTelemetryTrigger.SCHEDULED_DISPATCH)
        );
    }

    @Test
    void duplicateBookingEventDoesNotStartAnotherSearchWhileOfferIsActive() {
        BookingCreatedEvent event = event();
        when(candidateStore.findTripMatching(99L)).thenReturn(Optional.of(new TripMatchingState(
                99L,
                20L,
                1,
                Instant.parse("2026-05-20T04:00:00Z"),
                Set.of()
        )));
        BookingCreatedMatchingListener listener = new BookingCreatedMatchingListener(
                matchingService,
                candidateStore,
                driverOfferNotifier
        );

        listener.onBookingCreated(event);

        verify(matchingService, never()).findAndLockDriver(
                any(MatchingRequest.class),
                any(MatchingTelemetryTrigger.class)
        );
        verify(driverOfferNotifier, never()).notifyDriver(any(), any());
    }

    private BookingCreatedEvent event() {
        return event(BookingMatchingTrigger.BOOKING_CREATED);
    }

    private BookingCreatedEvent event(BookingMatchingTrigger trigger) {
        return new BookingCreatedEvent(
                99L,
                10L,
                VehicleType.MOTORBIKE,
                BigDecimal.valueOf(10.7769),
                BigDecimal.valueOf(106.7009),
                BigDecimal.valueOf(10.7850),
                BigDecimal.valueOf(106.6800),
                BigDecimal.valueOf(38000),
                trigger
        );
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
        return new DriverOffer(99L, candidate, 1, Instant.parse("2026-05-20T04:00:00Z"));
    }
}
