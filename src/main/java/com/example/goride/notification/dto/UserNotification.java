package com.example.goride.notification.dto;

import com.example.goride.booking.domain.Trip;
import com.example.goride.notification.domain.NotificationType;

import java.time.Instant;
import java.util.LinkedHashMap;
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

    public static UserNotification driverArrived(Trip trip) {
        return tripStatus(
                trip,
                NotificationType.DRIVER_ARRIVED,
                "Driver arrived",
                "Your driver has arrived at the pickup point"
        );
    }

    public static UserNotification tripStarted(Trip trip) {
        return tripStatus(
                trip,
                NotificationType.TRIP_STARTED,
                "Trip started",
                "Your trip has started"
        );
    }

    public static UserNotification tripCompleted(Trip trip) {
        return tripStatus(
                trip,
                NotificationType.TRIP_COMPLETED,
                "Trip completed",
                "Your trip is completed"
        );
    }

    private static UserNotification tripStatus(
            Trip trip,
            NotificationType type,
            String title,
            String body
    ) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tripId", trip.getId());
        data.put("status", trip.getStatus().name());
        if (trip.getDriver() != null) {
            data.put("driverId", trip.getDriver().getId());
        }
        if (trip.getFinalFare() != null) {
            data.put("finalFare", trip.getFinalFare());
        }

        return new UserNotification(
                type,
                title,
                body,
                data,
                Instant.now()
        );
    }
}
