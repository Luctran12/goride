package com.example.goride.chat.service;

import com.example.goride.chat.dto.TripMessageResponse;
import com.example.goride.chat.dto.TripMessageReadStateResponse;

public interface TripMessageRealtimeNotifier {
    void broadcastTripMessage(Long tripId, TripMessageResponse message);

    void broadcastReadState(Long tripId, TripMessageReadStateResponse readState);
}
