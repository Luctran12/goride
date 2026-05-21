package com.example.goride.notification.service;

import com.example.goride.notification.dto.TripStatusNotification;
import com.example.goride.notification.dto.UserNotification;

public interface TripRealtimeNotifier {
    void notifyPassenger(Long passengerId, UserNotification notification);

    void broadcastTripStatus(Long tripId, TripStatusNotification notification);
}
