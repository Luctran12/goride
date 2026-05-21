package com.example.goride.matching.service;

import com.example.goride.booking.event.BookingCreatedEvent;
import com.example.goride.driver.domain.VehicleType;
import com.example.goride.matching.domain.DriverCandidate;
import com.example.goride.matching.domain.DriverOffer;
import com.example.goride.matching.domain.MatchingRequest;
import com.example.goride.matching.notification.DriverOfferNotification;
import com.example.goride.matching.notification.DriverOfferNotifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingCreatedMatchingListenerTests {
    @Mock
    private MatchingService matchingService;

    @Mock
    private DriverOfferNotifier driverOfferNotifier;

    @Test
    void bookingCreatedTriggersMatchingAndSendsDriverOffer() {
        BookingCreatedEvent event = event();
        DriverOffer offer = offer();
        when(matchingService.findAndLockDriver(any(MatchingRequest.class))).thenReturn(Optional.of(offer));
        BookingCreatedMatchingListener listener = new BookingCreatedMatchingListener(
                matchingService,
                driverOfferNotifier
        );

        listener.onBookingCreated(event);

        ArgumentCaptor<MatchingRequest> requestCaptor = ArgumentCaptor.forClass(MatchingRequest.class);
        ArgumentCaptor<DriverOfferNotification> notificationCaptor =
                ArgumentCaptor.forClass(DriverOfferNotification.class);
        verify(matchingService).findAndLockDriver(requestCaptor.capture());
        verify(driverOfferNotifier).notifyDriver(org.mockito.Mockito.eq(20L), notificationCaptor.capture());
        assertThat(requestCaptor.getValue().tripId()).isEqualTo(99L);
        assertThat(requestCaptor.getValue().vehicleType()).isEqualTo(VehicleType.MOTORBIKE);
        assertThat(notificationCaptor.getValue().tripId()).isEqualTo(99L);
        assertThat(notificationCaptor.getValue().passengerId()).isEqualTo(10L);
        assertThat(notificationCaptor.getValue().distanceMeters()).isEqualTo(350L);
        assertThat(notificationCaptor.getValue().expiresAt()).isEqualTo(offer.offerExpiresAt());
    }

    @Test
    void bookingCreatedDoesNotNotifyWhenNoDriverCanBeLocked() {
        BookingCreatedEvent event = event();
        when(matchingService.findAndLockDriver(any(MatchingRequest.class))).thenReturn(Optional.empty());
        BookingCreatedMatchingListener listener = new BookingCreatedMatchingListener(
                matchingService,
                driverOfferNotifier
        );

        listener.onBookingCreated(event);

        verify(driverOfferNotifier, never()).notifyDriver(any(), any());
    }

    private BookingCreatedEvent event() {
        return new BookingCreatedEvent(
                99L,
                10L,
                VehicleType.MOTORBIKE,
                BigDecimal.valueOf(10.7769),
                BigDecimal.valueOf(106.7009),
                BigDecimal.valueOf(10.7850),
                BigDecimal.valueOf(106.6800),
                BigDecimal.valueOf(38000)
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
