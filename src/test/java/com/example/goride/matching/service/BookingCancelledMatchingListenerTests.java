package com.example.goride.matching.service;

import com.example.goride.booking.event.BookingCancelledEvent;
import com.example.goride.matching.domain.TripMatchingState;
import com.example.goride.matching.notification.DriverOfferCancelledNotification;
import com.example.goride.matching.notification.DriverOfferNotifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingCancelledMatchingListenerTests {
    @Mock
    private DriverCandidateStore candidateStore;

    @Mock
    private DriverOfferNotifier driverOfferNotifier;

    @Test
    void cancelledBookingClearsActiveOfferAndNotifiesOfferedDriver() {
        BookingCancelledEvent event = event();
        when(candidateStore.findTripMatching(99L)).thenReturn(Optional.of(new TripMatchingState(
                99L,
                20L,
                1,
                Instant.parse("2026-05-20T04:00:30Z"),
                Set.of()
        )));
        BookingCancelledMatchingListener listener = new BookingCancelledMatchingListener(
                candidateStore,
                driverOfferNotifier
        );

        listener.onBookingCancelled(event);

        ArgumentCaptor<DriverOfferCancelledNotification> notificationCaptor =
                ArgumentCaptor.forClass(DriverOfferCancelledNotification.class);
        verify(candidateStore).releaseCandidateLock(20L);
        verify(candidateStore).clearTripMatching(99L);
        verify(driverOfferNotifier).notifyOfferCancelled(eq(20L), notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().type()).isEqualTo("TRIP_CANCELLED");
        assertThat(notificationCaptor.getValue().action()).isEqualTo("DISMISS");
        assertThat(notificationCaptor.getValue().tripId()).isEqualTo(99L);
        assertThat(notificationCaptor.getValue().driverId()).isEqualTo(20L);
        assertThat(notificationCaptor.getValue().reason()).isEqualTo("Changed plan");
    }

    @Test
    void cancelledBookingWithoutActiveOfferOnlyClearsMatchingState() {
        BookingCancelledEvent event = event();
        when(candidateStore.findTripMatching(99L)).thenReturn(Optional.empty());
        BookingCancelledMatchingListener listener = new BookingCancelledMatchingListener(
                candidateStore,
                driverOfferNotifier
        );

        listener.onBookingCancelled(event);

        verify(candidateStore).clearTripMatching(99L);
        verify(candidateStore, never()).releaseCandidateLock(anyLong());
        verify(driverOfferNotifier, never()).notifyOfferCancelled(anyLong(), any());
    }

    private BookingCancelledEvent event() {
        return new BookingCancelledEvent(
                99L,
                10L,
                null,
                "Changed plan",
                Instant.parse("2026-05-20T04:00:00Z")
        );
    }
}
