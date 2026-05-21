package com.example.goride.tracking.service;

import com.example.goride.tracking.dto.DriverLocationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WebSocketTripLocationNotifierTests {
    @Test
    void broadcastsDriverLocationToTripTopic() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        WebSocketTripLocationNotifier notifier = new WebSocketTripLocationNotifier(messagingTemplate);
        DriverLocationResponse location = new DriverLocationResponse(
                99L,
                20L,
                BigDecimal.valueOf(10.78),
                BigDecimal.valueOf(106.69),
                BigDecimal.valueOf(92.5),
                BigDecimal.valueOf(28.4),
                Instant.parse("2026-05-21T08:00:00Z")
        );

        notifier.broadcastDriverLocation(99L, location);

        verify(messagingTemplate).convertAndSend("/topic/trip/99/location", location);
    }
}
