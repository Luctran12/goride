package com.example.goride.notification.service;

import com.example.goride.notification.dto.UserNotification;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InAppNotificationChannel implements UserNotificationChannel {
    private final NotificationInboxService notificationInboxService;

    public InAppNotificationChannel(NotificationInboxService notificationInboxService) {
        this.notificationInboxService = notificationInboxService;
    }

    @Override
    public String channelName() {
        return "in_app";
    }

    @Override
    public void send(Long userId, UserNotification notification) {
        notificationInboxService.saveInboxNotification(userId, notification);
    }
}
