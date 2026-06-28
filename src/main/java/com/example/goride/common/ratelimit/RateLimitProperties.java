package com.example.goride.common.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.security.rate-limit")
public record RateLimitProperties(
        Boolean enabled,
        int capacity,
        int refillTokens,
        long refillPeriodSeconds,
        int maxKeys,
        List<String> excludedPaths,
        Boolean useForwardedFor
) {
    public RateLimitProperties {
        enabled = enabled == null || enabled;
        capacity = positiveOrDefault(capacity, 120);
        refillTokens = positiveOrDefault(refillTokens, capacity);
        refillPeriodSeconds = positiveOrDefault(refillPeriodSeconds, 60L);
        maxKeys = positiveOrDefault(maxKeys, 10_000);
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

    private static int positiveOrDefault(int value, int defaultValue) {
        return value > 0 ? value : defaultValue;
    }

    private static long positiveOrDefault(long value, long defaultValue) {
        return value > 0 ? value : defaultValue;
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