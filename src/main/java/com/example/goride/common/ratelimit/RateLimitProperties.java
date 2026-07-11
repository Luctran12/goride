package com.example.goride.common.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.security.rate-limit")
public record RateLimitProperties(
        Boolean enabled,
        Store store,
        int capacity,
        int refillTokens,
        long refillPeriodSeconds,
        int maxKeys,
        String redisKeyPrefix,
        List<String> excludedPaths,
        Boolean useForwardedFor
) {
    private static final String DEFAULT_REDIS_KEY_PREFIX = "goride:rate-limit:";

    public RateLimitProperties {
        enabled = enabled == null || enabled;
        store = store == null ? Store.MEMORY : store;
        capacity = positiveOrDefault(capacity, 120);
        refillTokens = positiveOrDefault(refillTokens, capacity);
        refillPeriodSeconds = positiveOrDefault(refillPeriodSeconds, 60L);
        maxKeys = positiveOrDefault(maxKeys, 10_000);
        redisKeyPrefix = defaultString(redisKeyPrefix, DEFAULT_REDIS_KEY_PREFIX);
        excludedPaths = defaultList(
                excludedPaths,
                "/actuator/**",
                "/v3/api-docs/**",
                "/swagger-ui/**",
                "/swagger-ui.html",
                "/ws/**",
                "/ws-native/**"
        );
        useForwardedFor = useForwardedFor != null && useForwardedFor;
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public boolean shouldUseForwardedFor() {
        return Boolean.TRUE.equals(useForwardedFor);
    }

    public enum Store {
        MEMORY,
        REDIS
    }

    private static int positiveOrDefault(int value, int defaultValue) {
        return value > 0 ? value : defaultValue;
    }

    private static long positiveOrDefault(long value, long defaultValue) {
        return value > 0 ? value : defaultValue;
    }

    private static String defaultString(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static List<String> defaultList(List<String> values, String... defaults) {
        if (values == null || values.isEmpty()) {
            return List.of(defaults);
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
    }
}