package com.example.goride.notification.service;

import com.example.goride.notification.config.FcmPushProperties;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Message;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class FirebaseAdminMessagingGateway implements FirebaseMessagingGateway {
    private final FcmPushProperties properties;
    private volatile FirebaseMessaging firebaseMessaging;

    public FirebaseAdminMessagingGateway(FcmPushProperties properties) {
        this.properties = properties;
    }

    @Override
    public String send(Message message) {
        try {
            return firebaseMessaging().send(message);
        } catch (FirebaseMessagingException exception) {
            if (exception.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                throw FcmPushSendException.invalidToken("FCM token is not registered", exception);
            }
            throw new FcmPushSendException("Failed to send FCM message", exception);
        }
    }

    private FirebaseMessaging firebaseMessaging() {
        FirebaseMessaging existing = firebaseMessaging;
        if (existing != null) {
            return existing;
        }

        synchronized (this) {
            if (firebaseMessaging == null) {
                firebaseMessaging = FirebaseMessaging.getInstance(firebaseApp());
            }
            return firebaseMessaging;
        }
    }

    private FirebaseApp firebaseApp() {
        String appName = properties.getAppName();
        return FirebaseApp.getApps().stream()
                .filter(app -> app.getName().equals(appName))
                .findFirst()
                .orElseGet(() -> FirebaseApp.initializeApp(firebaseOptions(), appName));
    }

    private FirebaseOptions firebaseOptions() {
        if (!properties.hasServiceAccountPath()) {
            throw new FcmPushSendException(
                    "app.notifications.fcm.service-account-path is required to send FCM push"
            );
        }

        try (InputStream credentialsStream = Files.newInputStream(Path.of(properties.normalizedServiceAccountPath()))) {
            FirebaseOptions.Builder builder = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentialsStream));
            String projectId = properties.normalizedProjectId();
            if (projectId != null) {
                builder.setProjectId(projectId);
            }
            return builder.build();
        } catch (IOException exception) {
            throw new FcmPushSendException("Failed to initialize Firebase Admin SDK", exception);
        }
    }
}
