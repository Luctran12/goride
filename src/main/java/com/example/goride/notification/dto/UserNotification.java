package com.example.goride.notification.dto;

import com.example.goride.booking.domain.Trip;
import com.example.goride.notification.domain.NotificationType;

import java.time.Instant;
import java.util.Map;

public record UserNotification(
        NotificationType type,
        String title,
        String body,
        Map<String, Object> data,
        Instant createdAt
) {
    public UserNotification {
        data = data == null ? Map.of() : Map.copyOf(data);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public static UserNotification tripAccepted(Trip trip) {
        return new UserNotification(
                NotificationType.TRIP_ACCEPTED,
                "Trip accepted",
                "Your driver is on the way",
                Map.of(
                        "tripId", trip.getId(),
                        "status", trip.getStatus().name(),
                        "driverId", trip.getDriver().getId()
                ),
                Instant.now()
        );
    }

    public static UserNotification noDriverFound(Trip trip) {
        return new UserNotification(
                NotificationType.NO_DRIVER_FOUND,
                "No driver found",
                "No driver is available nearby",
                Map.of(
                        "tripId", trip.getId(),
                        "status", trip.getStatus().name()
                ),
                Instant.now()
        );
    }
}
