package com.example.goride.tracking.service;

import com.example.goride.tracking.dto.DriverLocationResponse;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class WebSocketTripLocationNotifier implements TripLocationNotifier {
    private static final String TRIP_LOCATION_TOPIC_TEMPLATE = "/topic/trip/%d/location";

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketTripLocationNotifier(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void broadcastDriverLocation(Long tripId, DriverLocationResponse location) {
        messagingTemplate.convertAndSend(TRIP_LOCATION_TOPIC_TEMPLATE.formatted(tripId), location);
    }
}
