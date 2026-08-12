package com.example.goride.chat.service;

import com.example.goride.chat.dto.TripMessageResponse;
import com.example.goride.chat.dto.TripMessageReadStateResponse;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class WebSocketTripMessageRealtimeNotifier implements TripMessageRealtimeNotifier {
    private static final String TRIP_MESSAGES_TOPIC_TEMPLATE = "/topic/trip/%d/messages";
    private static final String TRIP_MESSAGE_READ_TOPIC_TEMPLATE = "/topic/trip/%d/message-read";

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketTripMessageRealtimeNotifier(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void broadcastTripMessage(Long tripId, TripMessageResponse message) {
        messagingTemplate.convertAndSend(TRIP_MESSAGES_TOPIC_TEMPLATE.formatted(tripId), message);
    }

    @Override
    public void broadcastReadState(Long tripId, TripMessageReadStateResponse readState) {
        messagingTemplate.convertAndSend(TRIP_MESSAGE_READ_TOPIC_TEMPLATE.formatted(tripId), readState);
    }
}
