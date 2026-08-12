package com.example.goride.chat.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TripMessageRateLimitProperties.class)
public class TripMessageConfig {
}
