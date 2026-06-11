package com.example.goride.notification.service;

import com.example.goride.notification.dto.TripStatusNotification;
import com.example.goride.notification.dto.UserNotification;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WebSocketTripRealtimeNotifier implements TripRealtimeNotifier {
    private static final String TRIP_STATUS_TOPIC_TEMPLATE = "/topic/trip/%d/status";

    private final SimpMessagingTemplate messagingTemplate;
    private final List<UserNotificationChannel> userNotificationChannels;

    public WebSocketTripRealtimeNotifier(
            SimpMessagingTemplate messagingTemplate,
            List<UserNotificationChannel> userNotificationChannels
    ) {
        this.messagingTemplate = messagingTemplate;
        this.userNotificationChannels = List.copyOf(userNotificationChannels);
    }

    @Override
    public void notifyUser(Long userId, UserNotification notification) {
        userNotificationChannels.forEach(channel -> channel.send(userId, notification));
    }

    @Override
    public void broadcastTripStatus(Long tripId, TripStatusNotification notification) {
        messagingTemplate.convertAndSend(TRIP_STATUS_TOPIC_TEMPLATE.formatted(tripId), notification);
    }
}
