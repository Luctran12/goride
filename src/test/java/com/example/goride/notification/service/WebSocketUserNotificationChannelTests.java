package com.example.goride.notification.service;

import com.example.goride.notification.domain.NotificationType;
import com.example.goride.notification.dto.UserNotification;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WebSocketUserNotificationChannelTests {
    @Test
    void sendsNotificationToUserQueue() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        WebSocketUserNotificationChannel channel = new WebSocketUserNotificationChannel(messagingTemplate);
        UserNotification notification = notification();

        channel.send(10L, notification);

        verify(messagingTemplate).convertAndSendToUser("10", "/queue/notifications", notification);
        assertThat(channel.channelName()).isEqualTo("websocket");
    }

    private UserNotification notification() {
        return new UserNotification(
                NotificationType.TRIP_ACCEPTED,
                "Trip accepted",
                "Your driver is on the way",
                Map.of("tripId", 99L, "status", "ACCEPTED"),
                Instant.parse("2026-05-21T08:00:00Z")
        );
    }
}
