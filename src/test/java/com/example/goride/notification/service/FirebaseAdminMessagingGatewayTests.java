package com.example.goride.notification.service;

import com.example.goride.notification.config.FcmPushProperties;
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseException;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Message;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class FirebaseAdminMessagingGatewayTests {
    @Test
    void reportsConfiguredCredentialFileWhenItCannotBeRead() {
        FcmPushProperties properties = new FcmPushProperties();
        properties.setServiceAccountPath("missing/firebase-service-account.json");
        FirebaseAdminMessagingGateway gateway = new FirebaseAdminMessagingGateway(properties);
        Message message = Message.builder()
                .setToken("token-123")
                .build();

        assertThatThrownBy(() -> gateway.send(message))
                .isInstanceOf(FcmPushSendException.class)
                .hasMessage(
                        "Failed to load Firebase credentials from service account file "
                                + "missing/firebase-service-account.json"
                );
    }

    @Test
    void mapsUnregisteredFirebaseTokenToInvalidTokenFailure() throws Exception {
        FcmPushProperties properties = new FcmPushProperties();
        FirebaseAdminMessagingGateway gateway = new FirebaseAdminMessagingGateway(properties);
        FirebaseMessaging firebaseMessaging = mock(FirebaseMessaging.class);
        doThrow(firebaseMessagingException(MessagingErrorCode.UNREGISTERED))
                .when(firebaseMessaging)
                .send(any(Message.class));
        setFirebaseMessaging(gateway, firebaseMessaging);
        Message message = Message.builder()
                .setToken("token-123")
                .build();

        assertThatThrownBy(() -> gateway.send(message))
                .isInstanceOfSatisfying(FcmPushSendException.class, exception -> {
                    FcmPushSendException sendException = (FcmPushSendException) exception;
                    assertThat(sendException.isInvalidToken()).isTrue();
                });
    }

    private FirebaseMessagingException firebaseMessagingException(MessagingErrorCode errorCode) throws Exception {
        FirebaseException firebaseException = new FirebaseException(ErrorCode.NOT_FOUND, "FCM send failed", null);
        Method factory = FirebaseMessagingException.class.getDeclaredMethod(
                "withMessagingErrorCode",
                FirebaseException.class,
                MessagingErrorCode.class
        );
        factory.setAccessible(true);
        return (FirebaseMessagingException) factory.invoke(null, firebaseException, errorCode);
    }

    private void setFirebaseMessaging(
            FirebaseAdminMessagingGateway gateway,
            FirebaseMessaging firebaseMessaging
    ) throws Exception {
        Field field = FirebaseAdminMessagingGateway.class.getDeclaredField("firebaseMessaging");
        field.setAccessible(true);
        field.set(gateway, firebaseMessaging);
    }
}
