package com.example.goride.matching.notification;

import com.example.goride.booking.event.BookingCancelledEvent;

import java.time.Instant;

public record DriverOfferCancelledNotification(
        String type,
        String action,
        Long tripId,
        Long passengerId,
        Long driverId,
        String reason,
        Instant cancelledAt
) {
    private static final String TYPE = "TRIP_CANCELLED";
    private static final String ACTION = "DISMISS";

    public static DriverOfferCancelledNotification from(BookingCancelledEvent event, Long driverId) {
        return new DriverOfferCancelledNotification(
                TYPE,
                ACTION,
                event.tripId(),
                event.passengerId(),
                driverId,
                event.reason(),
                event.cancelledAt()
        );
    }
}