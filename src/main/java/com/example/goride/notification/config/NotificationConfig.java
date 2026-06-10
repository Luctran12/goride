package com.example.goride.notification.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FcmPushProperties.class)
public class NotificationConfig {
}
