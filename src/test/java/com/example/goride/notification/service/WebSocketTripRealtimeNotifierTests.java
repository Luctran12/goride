package com.example.goride.notification.service;

import com.example.goride.booking.domain.TripStatus;
import com.example.goride.notification.domain.NotificationType;
import com.example.goride.notification.dto.TripStatusNotification;
import com.example.goride.notification.dto.UserNotification;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WebSocketTripRealtimeNotifierTests {
    @Test
    void sendsPassengerNotificationToUserQueue() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        NotificationInboxService notificationInboxService = mock(NotificationInboxService.class);
        WebSocketTripRealtimeNotifier notifier = new WebSocketTripRealtimeNotifier(
                messagingTemplate,
                notificationInboxService
        );
        UserNotification notification = new UserNotification(
                NotificationType.TRIP_ACCEPTED,
                "Trip accepted",
                "Your driver is on the way",
                Map.of("tripId", 99L, "status", "ACCEPTED"),
                Instant.parse("2026-05-21T08:00:00Z")
        );

        notifier.notifyPassenger(10L, notification);

        verify(notificationInboxService).saveInboxNotification(10L, notification);
        verify(messagingTemplate).convertAndSendToUser("10", "/queue/notifications", notification);
    }

    @Test
    void broadcastsTripStatusToTopic() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        NotificationInboxService notificationInboxService = mock(NotificationInboxService.class);
        WebSocketTripRealtimeNotifier notifier = new WebSocketTripRealtimeNotifier(
                messagingTemplate,
                notificationInboxService
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
