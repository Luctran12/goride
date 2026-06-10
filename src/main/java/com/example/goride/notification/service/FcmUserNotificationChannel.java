package com.example.goride.notification.service;

import com.example.goride.notification.config.FcmPushProperties;
import com.example.goride.notification.dto.FcmPushMessage;
import com.example.goride.notification.dto.UserNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class FcmUserNotificationChannel implements UserNotificationChannel {
    private static final Logger log = LoggerFactory.getLogger(FcmUserNotificationChannel.class);

    private final FcmDeviceTokenStore fcmDeviceTokenStore;
    private final ObjectProvider<FcmPushSender> pushSenderProvider;
    private final FcmPushProperties properties;

    public FcmUserNotificationChannel(
            FcmDeviceTokenStore fcmDeviceTokenStore,
            ObjectProvider<FcmPushSender> pushSenderProvider,
            FcmPushProperties properties
    ) {
        this.fcmDeviceTokenStore = fcmDeviceTokenStore;
        this.pushSenderProvider = pushSenderProvider;
        this.properties = properties;
    }

    @Override
    public String channelName() {
        return "fcm";
    }

    @Override
    public void send(Long userId, UserNotification notification) {
        if (!properties.isEnabled()) {
            return;
        }

        FcmPushSender pushSender = pushSenderProvider.getIfAvailable();
        if (pushSender == null) {
            log.warn("Skipping FCM notification for user {} because no FcmPushSender bean is configured", userId);
            return;
        }

        fcmDeviceTokenStore.findToken(userId)
                .ifPresentOrElse(
                        token -> sendWithRetry(userId, token, notification, pushSender),
                        () -> log.debug("Skipping FCM notification for user {} because no device token is stored", userId)
                );
    }

    private void sendWithRetry(
            Long userId,
            String token,
            UserNotification notification,
            FcmPushSender pushSender
    ) {
        if (token == null || token.isBlank()) {
            log.warn("Skipping FCM notification for user {} because stored device token is blank", userId);
            return;
        }

        FcmPushMessage message = FcmPushMessage.from(token, notification);
        int maxAttempts = properties.getMaxAttempts();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                pushSender.send(message);
                return;
            } catch (FcmPushSendException exception) {
                if (exception.isInvalidToken()) {
                    fcmDeviceTokenStore.deleteToken(userId);
                    log.info("Removed invalid FCM token for user {}", userId);
                    return;
                }
                if (attempt == maxAttempts) {
                    log.warn(
                            "Failed to send FCM notification to user {} after {} attempts",
                            userId,
                            maxAttempts,
                            exception
                    );
                    return;
                }
                log.debug(
                        "Retrying FCM notification for user {} after attempt {} failed",
                        userId,
                        attempt,
                        exception
                );
            } catch (RuntimeException exception) {
                if (attempt == maxAttempts) {
                    log.warn(
                            "Failed to send FCM notification to user {} after {} attempts",
                            userId,
                            maxAttempts,
                            exception
                    );
                    return;
                }
                log.debug(
                        "Retrying FCM notification for user {} after attempt {} failed",
                        userId,
                        attempt,
                        exception
                );
            }
        }
    }
}
