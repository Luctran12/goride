package com.example.goride.notification.service;

import com.example.goride.notification.config.FcmPushProperties;
import com.example.goride.notification.domain.NotificationType;
import com.example.goride.notification.dto.FcmPushMessage;
import com.example.goride.notification.dto.UserNotification;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FcmUserNotificationChannelTests {
    @Test
    void skipsPushWhenFcmIsDisabled() {
        FcmDeviceTokenStore tokenStore = mock(FcmDeviceTokenStore.class);
        ObjectProvider<FcmPushSender> senderProvider = mock(ObjectProvider.class);
        FcmPushProperties properties = properties(false, 2);
        FcmUserNotificationChannel channel = new FcmUserNotificationChannel(tokenStore, senderProvider, properties);

        channel.send(10L, notification());

        verifyNoInteractions(tokenStore, senderProvider);
        assertThat(channel.channelName()).isEqualTo("fcm");
    }

    @Test
    void skipsPushWhenSenderBeanIsMissing() {
        FcmDeviceTokenStore tokenStore = mock(FcmDeviceTokenStore.class);
        ObjectProvider<FcmPushSender> senderProvider = senderProvider(null);
        FcmUserNotificationChannel channel = new FcmUserNotificationChannel(tokenStore, senderProvider, properties(true, 2));

        channel.send(10L, notification());

        verifyNoInteractions(tokenStore);
    }

    @Test
    void skipsPushWhenUserHasNoStoredToken() {
        FcmDeviceTokenStore tokenStore = mock(FcmDeviceTokenStore.class);
        FcmPushSender sender = mock(FcmPushSender.class);
        when(tokenStore.findToken(10L)).thenReturn(Optional.empty());
        FcmUserNotificationChannel channel = new FcmUserNotificationChannel(
                tokenStore,
                senderProvider(sender),
                properties(true, 2)
        );

        channel.send(10L, notification());

        verify(tokenStore).findToken(10L);
        verifyNoInteractions(sender);
    }

    @Test
    void skipsPushWhenStoredTokenIsBlank() {
        FcmDeviceTokenStore tokenStore = mock(FcmDeviceTokenStore.class);
        FcmPushSender sender = mock(FcmPushSender.class);
        when(tokenStore.findToken(10L)).thenReturn(Optional.of("  "));
        FcmUserNotificationChannel channel = new FcmUserNotificationChannel(
                tokenStore,
                senderProvider(sender),
                properties(true, 2)
        );

        channel.send(10L, notification());

        verify(tokenStore).findToken(10L);
        verifyNoInteractions(sender);
    }

    @Test
    void sendsPushWhenEnabledAndTokenExists() {
        FcmDeviceTokenStore tokenStore = mock(FcmDeviceTokenStore.class);
        FcmPushSender sender = mock(FcmPushSender.class);
        when(tokenStore.findToken(10L)).thenReturn(Optional.of("token-123"));
        FcmUserNotificationChannel channel = new FcmUserNotificationChannel(
                tokenStore,
                senderProvider(sender),
                properties(true, 2)
        );

        channel.send(10L, notification());

        var messageCaptor = forClass(FcmPushMessage.class);
        verify(sender).send(messageCaptor.capture());
        assertThat(messageCaptor.getValue().token()).isEqualTo("token-123");
        assertThat(messageCaptor.getValue().title()).isEqualTo("Trip accepted");
        assertThat(messageCaptor.getValue().data())
                .containsEntry("tripId", "99")
                .containsEntry("status", "ACCEPTED");
    }

    @Test
    void retriesPushFailureAndSuppressesAfterSuccess() {
        FcmDeviceTokenStore tokenStore = mock(FcmDeviceTokenStore.class);
        FcmPushSender sender = mock(FcmPushSender.class);
        when(tokenStore.findToken(10L)).thenReturn(Optional.of("token-123"));
        doThrow(new FcmPushSendException("temporary failure"))
                .doNothing()
                .when(sender)
                .send(any(FcmPushMessage.class));
        FcmUserNotificationChannel channel = new FcmUserNotificationChannel(
                tokenStore,
                senderProvider(sender),
                properties(true, 2)
        );

        channel.send(10L, notification());

        verify(sender, times(2)).send(any(FcmPushMessage.class));
        verify(tokenStore, never()).deleteToken(10L);
    }

    @Test
    void removesStoredTokenWhenFirebaseReportsInvalidToken() {
        FcmDeviceTokenStore tokenStore = mock(FcmDeviceTokenStore.class);
        FcmPushSender sender = mock(FcmPushSender.class);
        when(tokenStore.findToken(10L)).thenReturn(Optional.of("token-123"));
        doThrow(FcmPushSendException.invalidToken("FCM token is not registered", null))
                .when(sender)
                .send(any(FcmPushMessage.class));
        FcmUserNotificationChannel channel = new FcmUserNotificationChannel(
                tokenStore,
                senderProvider(sender),
                properties(true, 2)
        );

        channel.send(10L, notification());

        verify(sender).send(any(FcmPushMessage.class));
        verify(tokenStore).deleteToken(10L);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<FcmPushSender> senderProvider(FcmPushSender sender) {
        ObjectProvider<FcmPushSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        return provider;
    }

    private FcmPushProperties properties(boolean enabled, int maxAttempts) {
        FcmPushProperties properties = new FcmPushProperties();
        properties.setEnabled(enabled);
        properties.setMaxAttempts(maxAttempts);
        return properties;
    }

    private UserNotification notification() {
        return new UserNotification(
                NotificationType.TRIP_ACCEPTED,
                "Trip accepted",
                "Your driver is on the way",
                Map.of("tripId", 99L, "status", "ACCEPTED"),
                Instant.parse("2026-05-21T08:00:00Z")
        );
    }
}
