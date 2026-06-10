package com.example.goride.notification.service;

import com.example.goride.notification.config.FcmPushProperties;
import com.google.firebase.messaging.Message;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FirebaseAdminMessagingGatewayTests {
    @Test
    void rejectsSendWhenServiceAccountPathIsMissing() {
        FcmPushProperties properties = new FcmPushProperties();
        FirebaseAdminMessagingGateway gateway = new FirebaseAdminMessagingGateway(properties);
        Message message = Message.builder()
                .setToken("token-123")
                .build();

        assertThatThrownBy(() -> gateway.send(message))
                .isInstanceOf(FcmPushSendException.class)
                .hasMessage("app.notifications.fcm.service-account-path is required to send FCM push");
    }
}
