package com.example.goride.notification.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class FirebaseAdminStartupValidatorTests {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(FirebaseMessagingGateway.class, () -> mock(FirebaseMessagingGateway.class))
            .withUserConfiguration(FirebaseAdminStartupValidator.class);

    @Test
    void initializesFirebaseWhenFcmIsEnabled() {
        contextRunner
                .withPropertyValues("app.notifications.fcm.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(FirebaseAdminStartupValidator.class);
                    verify(context.getBean(FirebaseMessagingGateway.class)).initialize();
                });
    }

    @Test
    void skipsFirebaseInitializationWhenFcmIsDisabled() {
        contextRunner
                .withPropertyValues("app.notifications.fcm.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(FirebaseAdminStartupValidator.class);
                    verifyNoInteractions(context.getBean(FirebaseMessagingGateway.class));
                });
    }
}
