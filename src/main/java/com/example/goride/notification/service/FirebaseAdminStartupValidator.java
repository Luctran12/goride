package com.example.goride.notification.service;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "app.notifications.fcm",
        name = "enabled",
        havingValue = "true"
)
public class FirebaseAdminStartupValidator implements InitializingBean {
    private final FirebaseMessagingGateway messagingGateway;

    public FirebaseAdminStartupValidator(FirebaseMessagingGateway messagingGateway) {
        this.messagingGateway = messagingGateway;
    }

    @Override
    public void afterPropertiesSet() {
        messagingGateway.initialize();
    }
}
