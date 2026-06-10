package com.example.goride.notification.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FcmPushPropertiesTests {
    @Test
    void exposesSafeDefaults() {
        FcmPushProperties properties = new FcmPushProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getMaxAttempts()).isEqualTo(2);
        assertThat(properties.getAppName()).isEqualTo("goride");
        assertThat(properties.hasServiceAccountPath()).isFalse();
    }

    @Test
    void normalizesFirebaseAdminSettings() {
        FcmPushProperties properties = new FcmPushProperties();

        properties.setServiceAccountPath("  C:/secrets/firebase.json  ");
        properties.setProjectId("  goride-prod  ");
        properties.setAppName("  goride-fcm  ");

        assertThat(properties.hasServiceAccountPath()).isTrue();
        assertThat(properties.normalizedServiceAccountPath()).isEqualTo("C:/secrets/firebase.json");
        assertThat(properties.normalizedProjectId()).isEqualTo("goride-prod");
        assertThat(properties.getAppName()).isEqualTo("goride-fcm");
    }

    @Test
    void rejectsInvalidMaxAttemptsAndBlankAppName() {
        FcmPushProperties properties = new FcmPushProperties();

        assertThatThrownBy(() -> properties.setMaxAttempts(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("app.notifications.fcm.max-attempts must be at least 1");
        assertThatThrownBy(() -> properties.setAppName("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("app.notifications.fcm.app-name must not be blank");
    }
}
