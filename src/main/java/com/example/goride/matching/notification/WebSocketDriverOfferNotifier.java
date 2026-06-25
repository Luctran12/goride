package com.example.goride.matching.notification;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class WebSocketDriverOfferNotifier implements DriverOfferNotifier {
    private static final String TRIP_REQUEST_QUEUE = "/queue/trip-requests";

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketDriverOfferNotifier(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void notifyDriver(Long driverId, DriverOfferNotification notification) {
        sendToTripRequestQueue(driverId, notification);
    }

    @Override
    public void notifyOfferCancelled(Long driverId, DriverOfferCancelledNotification notification) {
        sendToTripRequestQueue(driverId, notification);
    }

    private void sendToTripRequestQueue(Long driverId, Object notification) {
        messagingTemplate.convertAndSendToUser(
                String.valueOf(driverId),
                TRIP_REQUEST_QUEUE,
                notification
        );
    }
}
