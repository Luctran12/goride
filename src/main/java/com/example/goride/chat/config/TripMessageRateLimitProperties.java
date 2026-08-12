package com.example.goride.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.chat.rate-limit")
public record TripMessageRateLimitProperties(
        Boolean enabled,
        int capacity,
        int refillTokens,
        long refillPeriodSeconds,
        String redisKeyPrefix
) {
    private static final String DEFAULT_KEY_PREFIX = "goride:chat-rate-limit:";

    public TripMessageRateLimitProperties {
        enabled = enabled == null || enabled;
        capacity = capacity > 0 ? capacity : 30;
        refillTokens = refillTokens > 0 ? refillTokens : capacity;
        refillPeriodSeconds = refillPeriodSeconds > 0 ? refillPeriodSeconds : 60;
        redisKeyPrefix = redisKeyPrefix == null || redisKeyPrefix.isBlank()
                ? DEFAULT_KEY_PREFIX
                : redisKeyPrefix.trim();
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }
}
