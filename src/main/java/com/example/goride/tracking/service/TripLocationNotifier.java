package com.example.goride.tracking.service;

import com.example.goride.tracking.dto.DriverLocationResponse;

public interface TripLocationNotifier {
    void broadcastDriverLocation(Long tripId, DriverLocationResponse location);
}
