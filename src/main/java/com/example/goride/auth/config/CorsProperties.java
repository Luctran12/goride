package com.example.goride.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.security.cors")
public record CorsProperties(
        List<String> allowedOrigins,
        List<String> allowedOriginPatterns,
        List<String> allowedMethods,
        List<String> allowedHeaders,
        List<String> exposedHeaders,
        boolean allowCredentials,
        long maxAgeSeconds
) {
    public CorsProperties {
        allowedOrigins = defaultList(allowedOrigins);
        allowedOriginPatterns = defaultList(allowedOriginPatterns);
        allowedMethods = defaultList(allowedMethods, "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        allowedHeaders = defaultList(allowedHeaders, "Authorization", "Content-Type", "X-Request-Id");
        exposedHeaders = defaultList(
                exposedHeaders,
                "X-Request-Id",
                "Retry-After",
                "X-RateLimit-Limit",
                "X-RateLimit-Remaining",
                "X-RateLimit-Reset"
        );
        maxAgeSeconds = maxAgeSeconds > 0 ? maxAgeSeconds : 3600;
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