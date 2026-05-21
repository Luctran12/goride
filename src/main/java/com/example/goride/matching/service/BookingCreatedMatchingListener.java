package com.example.goride.matching.service;

import com.example.goride.booking.event.BookingCreatedEvent;
import com.example.goride.matching.domain.DriverOffer;
import com.example.goride.matching.domain.MatchingRequest;
import com.example.goride.matching.notification.DriverOfferNotification;
import com.example.goride.matching.notification.DriverOfferNotifier;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class BookingCreatedMatchingListener {
    private final MatchingService matchingService;
    private final DriverOfferNotifier driverOfferNotifier;

    public BookingCreatedMatchingListener(
            MatchingService matchingService,
            DriverOfferNotifier driverOfferNotifier
    ) {
        this.matchingService = matchingService;
        this.driverOfferNotifier = driverOfferNotifier;
    }

    @EventListener
    public void onBookingCreated(BookingCreatedEvent event) {
        matchingService.findAndLockDriver(MatchingRequest.from(event))
                .ifPresent(offer -> notifyDriver(event, offer));
    }

    private void notifyDriver(BookingCreatedEvent event, DriverOffer offer) {
        driverOfferNotifier.notifyDriver(
                offer.candidate().driverId(),
                DriverOfferNotification.from(event, offer)
        );
    }
}
