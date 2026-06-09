package com.example.goride.notification.dto;

import com.example.goride.notification.domain.Notification;
import com.example.goride.notification.domain.NotificationType;

import java.time.Instant;
import java.util.Map;

public record NotificationResponse(
        Long notificationId,
        NotificationType type,
        String title,
        String body,
        Map<String, Object> data,
        boolean read,
        Instant readAt,
        Instant createdAt
) {
    public static NotificationResponse from(Notification notification, Map<String, Object> data) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                data == null ? Map.of() : Map.copyOf(data),
                notification.isRead(),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }
}
