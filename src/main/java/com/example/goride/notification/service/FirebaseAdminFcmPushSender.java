package com.example.goride.notification.service;

import com.example.goride.notification.dto.FcmPushMessage;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.springframework.stereotype.Component;

@Component
public class FirebaseAdminFcmPushSender implements FcmPushSender {
    private final FirebaseMessagingGateway messagingGateway;

    public FirebaseAdminFcmPushSender(FirebaseMessagingGateway messagingGateway) {
        this.messagingGateway = messagingGateway;
    }

    @Override
    public void send(FcmPushMessage message) {
        messagingGateway.send(toFirebaseMessage(message));
    }

    Message toFirebaseMessage(FcmPushMessage message) {
        return Message.builder()
                .setToken(message.token())
                .setNotification(Notification.builder()
                        .setTitle(message.title())
                        .setBody(message.body())
                        .build())
                .putAllData(message.data())
                .build();
    }
}
