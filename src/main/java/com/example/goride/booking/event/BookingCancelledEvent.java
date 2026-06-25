package com.example.goride.booking.event;

import com.example.goride.booking.domain.Trip;

import java.time.Instant;

public record BookingCancelledEvent(
        Long tripId,
        Long passengerId,
        Long driverId,
        String reason,
        Instant cancelledAt
) {
    public static BookingCancelledEvent from(Trip trip) {
        Long driverId = trip.getDriver() == null ? null : trip.getDriver().getId();
        return new BookingCancelledEvent(
                trip.getId(),
                trip.getPassenger().getId(),
                driverId,
                trip.getCancelReason(),
                trip.getCancelledAt()
        );
    }
}