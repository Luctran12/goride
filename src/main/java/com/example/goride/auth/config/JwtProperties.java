package com.example.goride.auth.config;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        long accessTokenMinutes,
        long refreshTokenDays
) {
    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.jwt.secret must be at least 32 bytes for HS256");
        }
        if (accessTokenMinutes <= 0) {
            throw new IllegalStateException("app.jwt.access-token-minutes must be positive");
        }
        if (refreshTokenDays <= 0) {
            throw new IllegalStateException("app.jwt.refresh-token-days must be positive");
        }
    }
}
