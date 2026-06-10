package com.example.goride.notification.service;

import com.example.goride.notification.dto.FcmPushMessage;
import com.google.firebase.messaging.Message;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FirebaseAdminFcmPushSenderTests {
    @Test
    void sendsFirebaseMessageThroughGateway() {
        FirebaseMessagingGateway messagingGateway = mock(FirebaseMessagingGateway.class);
        FirebaseAdminFcmPushSender sender = new FirebaseAdminFcmPushSender(messagingGateway);
        FcmPushMessage message = new FcmPushMessage(
                "token-123",
                "Trip accepted",
                "Your driver is on the way",
                Map.of("tripId", "99", "status", "ACCEPTED")
        );

        sender.send(message);

        var messageCaptor = forClass(Message.class);
        verify(messagingGateway).send(messageCaptor.capture());
        assertThat(messageCaptor.getValue()).isNotNull();
    }
}
