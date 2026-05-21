package com.example.goride.notification.service;

import com.example.goride.notification.dto.TripStatusNotification;
import com.example.goride.notification.dto.UserNotification;

public interface TripRealtimeNotifier {
    void notifyUser(Long userId, UserNotification notification);

    default void notifyPassenger(Long passengerId, UserNotification notification) {
        notifyUser(passengerId, notification);
    }

    void broadcastTripStatus(Long tripId, TripStatusNotification notification);
}
