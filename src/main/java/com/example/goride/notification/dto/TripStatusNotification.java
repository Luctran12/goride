package com.example.goride.notification.dto;

import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.domain.TripStatus;

import java.time.Instant;

public record TripStatusNotification(
        Long tripId,
        TripStatus status,
        Instant updatedAt
) {
    public static TripStatusNotification from(Trip trip) {
        return new TripStatusNotification(trip.getId(), trip.getStatus(), Instant.now());
    }
}
