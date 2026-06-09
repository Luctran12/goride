package com.example.goride.notification.service;

import com.example.goride.notification.dto.TripStatusNotification;
import com.example.goride.notification.dto.UserNotification;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class WebSocketTripRealtimeNotifier implements TripRealtimeNotifier {
    private static final String USER_NOTIFICATIONS_QUEUE = "/queue/notifications";
    private static final String TRIP_STATUS_TOPIC_TEMPLATE = "/topic/trip/%d/status";

    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationInboxService notificationInboxService;

    public WebSocketTripRealtimeNotifier(
            SimpMessagingTemplate messagingTemplate,
            NotificationInboxService notificationInboxService
    ) {
        this.messagingTemplate = messagingTemplate;
        this.notificationInboxService = notificationInboxService;
    }

    @Override
    public void notifyUser(Long userId, UserNotification notification) {
        notificationInboxService.saveInboxNotification(userId, notification);
        messagingTemplate.convertAndSendToUser(
                String.valueOf(userId),
                USER_NOTIFICATIONS_QUEUE,
                notification
        );
    }

    @Override
    public void broadcastTripStatus(Long tripId, TripStatusNotification notification) {
        messagingTemplate.convertAndSend(TRIP_STATUS_TOPIC_TEMPLATE.formatted(tripId), notification);
    }
}
