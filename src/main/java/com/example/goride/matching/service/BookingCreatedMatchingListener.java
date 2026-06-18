package com.example.goride.matching.service;

import com.example.goride.booking.event.BookingCreatedEvent;
import com.example.goride.matching.domain.DriverOffer;
import com.example.goride.matching.domain.MatchingRequest;
import com.example.goride.matching.notification.DriverOfferNotification;
import com.example.goride.matching.notification.DriverOfferNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class BookingCreatedMatchingListener {
    private static final Logger log = LoggerFactory.getLogger(BookingCreatedMatchingListener.class);

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
                .ifPresentOrElse(
                        offer -> notifyDriver(event, offer),
                        () -> log.info(
                                "Initial matching found no driver tripId={} status=SEARCHING",
                                event.tripId()
                        )
                );
    }

    private void notifyDriver(BookingCreatedEvent event, DriverOffer offer) {
        log.info(
                "Initial matching offer created tripId={} driverId={} attempt={} expiresAt={}",
                event.tripId(),
                offer.candidate().driverId(),
                offer.attempt(),
                offer.offerExpiresAt()
        );
        driverOfferNotifier.notifyDriver(
                offer.candidate().driverId(),
                DriverOfferNotification.from(event, offer)
        );
    }
}
