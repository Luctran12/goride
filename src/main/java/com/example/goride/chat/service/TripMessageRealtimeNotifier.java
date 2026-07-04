package com.example.goride.chat.service;

import com.example.goride.chat.dto.TripMessageResponse;

public interface TripMessageRealtimeNotifier {
    void broadcastTripMessage(Long tripId, TripMessageResponse message);
}
