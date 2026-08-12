package com.example.goride.chat.service;

import com.example.goride.chat.dto.TripMessageResponse;
import com.example.goride.notification.dto.UserNotification;
import com.example.goride.notification.service.FcmUserNotificationChannel;
import org.springframework.stereotype.Component;

@Component
public class FcmTripMessagePushNotifier implements TripMessagePushNotifier {
    private final FcmUserNotificationChannel fcmChannel;

    public FcmTripMessagePushNotifier(FcmUserNotificationChannel fcmChannel) {
        this.fcmChannel = fcmChannel;
    }

    @Override
    public void notifyRecipient(Long recipientId, TripMessageResponse message) {
        fcmChannel.send(recipientId, UserNotification.tripMessage(message));
    }
}
