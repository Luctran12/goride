package com.example.goride.notification.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FcmPushSendExceptionTests {
    @Test
    void marksInvalidTokenFailures() {
        FcmPushSendException exception = FcmPushSendException.invalidToken("FCM token is not registered", null);

        assertThat(exception.isInvalidToken()).isTrue();
        assertThat(exception).hasMessage("FCM token is not registered");
    }

    @Test
    void regularFailuresAreNotInvalidTokenFailures() {
        FcmPushSendException exception = new FcmPushSendException("temporary failure");

        assertThat(exception.isInvalidToken()).isFalse();
    }
}
