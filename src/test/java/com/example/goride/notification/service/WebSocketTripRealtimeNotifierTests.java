package com.example.goride.notification.service;

import com.example.goride.booking.domain.TripStatus;
import com.example.goride.notification.domain.NotificationType;
import com.example.goride.notification.dto.TripStatusNotification;
import com.example.goride.notification.dto.UserNotification;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WebSocketTripRealtimeNotifierTests {
    @Test
    void sendsPassengerNotificationToConfiguredChannelsInOrder() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        UserNotificationChannel inboxChannel = mock(UserNotificationChannel.class);
        UserNotificationChannel websocketChannel = mock(UserNotificationChannel.class);
        WebSocketTripRealtimeNotifier notifier = new WebSocketTripRealtimeNotifier(
                messagingTemplate,
                List.of(inboxChannel, websocketChannel)
        );
        UserNotification notification = new UserNotification(
                NotificationType.TRIP_ACCEPTED,
                "Trip accepted",
                "Your driver is on the way",
                Map.of("tripId", 99L, "status", "ACCEPTED"),
                Instant.parse("2026-05-21T08:00:00Z")
        );

        notifier.notifyPassenger(10L, notification);

        var inOrder = inOrder(inboxChannel, websocketChannel);
        inOrder.verify(inboxChannel).send(10L, notification);
        inOrder.verify(websocketChannel).send(10L, notification);
    }

    @Test
    void broadcastsTripStatusToTopic() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        WebSocketTripRealtimeNotifier notifier = new WebSocketTripRealtimeNotifier(
                messagingTemplate,
                List.of()
        );
        TripStatusNotification notification = new TripStatusNotification(
                99L,
                TripStatus.ACCEPTED,
                Instant.parse("2026-05-21T08:00:00Z")
        );

        notifier.broadcastTripStatus(99L, notification);

        verify(messagingTemplate).convertAndSend("/topic/trip/99/status", notification);
    }
}
