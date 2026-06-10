package com.example.goride.notification.service;

import com.example.goride.notification.dto.UserNotification;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class WebSocketUserNotificationChannel implements UserNotificationChannel {
    private static final String USER_NOTIFICATIONS_QUEUE = "/queue/notifications";

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketUserNotificationChannel(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public String channelName() {
        return "websocket";
    }

    @Override
    public void send(Long userId, UserNotification notification) {
        messagingTemplate.convertAndSendToUser(
                String.valueOf(userId),
                USER_NOTIFICATIONS_QUEUE,
                notification
        );
    }
}
