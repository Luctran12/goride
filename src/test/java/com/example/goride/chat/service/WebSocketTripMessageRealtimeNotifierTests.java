package com.example.goride.chat.service;

import com.example.goride.chat.domain.TripMessageSenderRole;
import com.example.goride.chat.dto.TripMessageResponse;
import com.example.goride.chat.dto.TripMessageReadStateResponse;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WebSocketTripMessageRealtimeNotifierTests {
    @Test
    void broadcastsTripMessageToTripTopic() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        WebSocketTripMessageRealtimeNotifier notifier = new WebSocketTripMessageRealtimeNotifier(messagingTemplate);
        TripMessageResponse message = new TripMessageResponse(
                1L,
                99L,
                10L,
                TripMessageSenderRole.PASSENGER,
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "Hello",
                Instant.parse("2026-07-01T10:00:00Z")
        );

        notifier.broadcastTripMessage(99L, message);

        verify(messagingTemplate).convertAndSend("/topic/trip/99/messages", message);
    }

    @Test
    void broadcastsReadStateToTripTopic() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        WebSocketTripMessageRealtimeNotifier notifier = new WebSocketTripMessageRealtimeNotifier(messagingTemplate);
        TripMessageReadStateResponse readState = new TripMessageReadStateResponse(
                99L,
                10L,
                1L,
                Instant.parse("2026-07-01T10:01:00Z"),
                0
        );

        notifier.broadcastReadState(99L, readState);

        verify(messagingTemplate).convertAndSend("/topic/trip/99/message-read", readState);
    }
}
