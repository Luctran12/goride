package com.example.goride.chat.service;

import com.example.goride.chat.domain.TripMessageSenderRole;
import com.example.goride.chat.dto.TripMessageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;

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
                "Hello",
                Instant.parse("2026-07-01T10:00:00Z")
        );

        notifier.broadcastTripMessage(99L, message);

        verify(messagingTemplate).convertAndSend("/topic/trip/99/messages", message);
    }
}
