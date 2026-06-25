package com.example.goride.matching.notification;

import com.example.goride.driver.domain.VehicleType;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WebSocketDriverOfferNotifierTests {
    @Test
    void sendsOfferToUserTripRequestsQueue() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        WebSocketDriverOfferNotifier notifier = new WebSocketDriverOfferNotifier(messagingTemplate);
        DriverOfferNotification notification = new DriverOfferNotification(
                99L,
                10L,
                VehicleType.MOTORBIKE,
                BigDecimal.valueOf(10.7769),
                BigDecimal.valueOf(106.7009),
                BigDecimal.valueOf(10.7850),
                BigDecimal.valueOf(106.6800),
                BigDecimal.valueOf(38000),
                350L,
                Instant.parse("2026-05-20T04:00:00Z")
        );

        notifier.notifyDriver(20L, notification);

        verify(messagingTemplate).convertAndSendToUser("20", "/queue/trip-requests", notification);
    }

    @Test
    void sendsCancelledOfferToUserTripRequestsQueue() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        WebSocketDriverOfferNotifier notifier = new WebSocketDriverOfferNotifier(messagingTemplate);
        DriverOfferCancelledNotification notification = new DriverOfferCancelledNotification(
                "TRIP_CANCELLED",
                "DISMISS",
                99L,
                10L,
                20L,
                "Changed plan",
                Instant.parse("2026-05-20T04:00:00Z")
        );

        notifier.notifyOfferCancelled(20L, notification);

        verify(messagingTemplate).convertAndSendToUser("20", "/queue/trip-requests", notification);
    }
}
