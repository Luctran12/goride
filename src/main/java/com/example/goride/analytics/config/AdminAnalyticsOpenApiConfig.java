package com.example.goride.analytics.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(
        name = AdminAnalyticsOpenApiConfig.BEARER_AUTH,
        description = "JWT access token issued by POST /api/v1/auth/login. ROLE_ADMIN is required.",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class AdminAnalyticsOpenApiConfig {
    public static final String BEARER_AUTH = "bearerAuth";
}
