package com.example.goride.notification.dto;

import com.example.goride.notification.domain.NotificationType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FcmPushMessageTests {
    @Test
    void fromUserNotificationConvertsDataToStringValues() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tripId", 99L);
        data.put("status", "ACCEPTED");
        UserNotification notification = new UserNotification(
                NotificationType.TRIP_ACCEPTED,
                "Trip accepted",
                "Your driver is on the way",
                data,
                Instant.parse("2026-05-21T08:00:00Z")
        );

        FcmPushMessage message = FcmPushMessage.from("  token-123  ", notification);

        assertThat(message.token()).isEqualTo("token-123");
        assertThat(message.title()).isEqualTo("Trip accepted");
        assertThat(message.body()).isEqualTo("Your driver is on the way");
        assertThat(message.data())
                .containsEntry("tripId", "99")
                .containsEntry("status", "ACCEPTED");
    }

    @Test
    void rejectsBlankToken() {
        UserNotification notification = new UserNotification(
                NotificationType.TRIP_ACCEPTED,
                "Trip accepted",
                "Your driver is on the way",
                Map.of(),
                Instant.parse("2026-05-21T08:00:00Z")
        );

        assertThatThrownBy(() -> FcmPushMessage.from("  ", notification))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("FCM token is required");
    }
}
