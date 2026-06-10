package com.example.goride.notification.service;

import com.example.goride.notification.domain.NotificationType;
import com.example.goride.notification.dto.UserNotification;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InAppNotificationChannelTests {
    @Test
    void savesNotificationToInbox() {
        NotificationInboxService notificationInboxService = mock(NotificationInboxService.class);
        InAppNotificationChannel channel = new InAppNotificationChannel(notificationInboxService);
        UserNotification notification = notification();

        channel.send(10L, notification);

        verify(notificationInboxService).saveInboxNotification(10L, notification);
        assertThat(channel.channelName()).isEqualTo("in_app");
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
