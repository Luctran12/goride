package com.example.goride.matching.service;

import com.example.goride.booking.event.BookingCancelledEvent;
import com.example.goride.matching.domain.TripMatchingState;
import com.example.goride.matching.notification.DriverOfferCancelledNotification;
import com.example.goride.matching.notification.DriverOfferNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class BookingCancelledMatchingListener {
    private static final Logger log = LoggerFactory.getLogger(BookingCancelledMatchingListener.class);

    private final DriverCandidateStore candidateStore;
    private final DriverOfferNotifier driverOfferNotifier;

    public BookingCancelledMatchingListener(
            DriverCandidateStore candidateStore,
            DriverOfferNotifier driverOfferNotifier
    ) {
        this.candidateStore = candidateStore;
        this.driverOfferNotifier = driverOfferNotifier;
    }

    @EventListener
    public void onBookingCancelled(BookingCancelledEvent event) {
        Optional<TripMatchingState> matchingState = candidateStore.findTripMatching(event.tripId());
        if (matchingState.isEmpty()) {
            candidateStore.clearTripMatching(event.tripId());
            log.info("Booking cancelled with no active matching offer tripId={}", event.tripId());
            return;
        }

        Long offeredDriverId = matchingState.get().offeredDriverId();
        candidateStore.releaseCandidateLock(offeredDriverId);
        candidateStore.clearTripMatching(event.tripId());
        driverOfferNotifier.notifyOfferCancelled(
                offeredDriverId,
                DriverOfferCancelledNotification.from(event, offeredDriverId)
        );
        log.info(
                "Cancelled booking matching offer cleared tripId={} driverId={}",
                event.tripId(),
                offeredDriverId
        );
    }
}