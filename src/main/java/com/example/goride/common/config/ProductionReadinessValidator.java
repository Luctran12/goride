package com.example.goride.common.config;

import com.example.goride.auth.config.CorsProperties;
import com.example.goride.auth.config.JwtProperties;
import com.example.goride.storage.config.FileStorageProperties;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class ProductionReadinessValidator implements SmartInitializingSingleton {
    private static final String DEFAULT_DEV_JWT_SECRET = "local-dev-secret-change-me-please-change";
    private static final Set<String> PRODUCTION_ENVIRONMENTS = Set.of("prod", "production");
    private static final Set<String> UNSAFE_DDL_AUTO_VALUES = Set.of("update", "create", "create-drop");

    private final Environment environment;
    private final JwtProperties jwtProperties;
    private final CorsProperties corsProperties;
    private final FileStorageProperties storageProperties;

    public ProductionReadinessValidator(
            Environment environment,
            JwtProperties jwtProperties,
            CorsProperties corsProperties,
            FileStorageProperties storageProperties
    ) {
        this.environment = environment;
        this.jwtProperties = jwtProperties;
        this.corsProperties = corsProperties;
        this.storageProperties = storageProperties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        validate();
    }

    public void validate() {
        List<String> violations = findViolations();
        if (!violations.isEmpty()) {
            throw new IllegalStateException("Production readiness check failed: " + String.join("; ", violations));
        }
    }

    List<String> findViolations() {
        if (!isProductionEnvironment()) {
            return List.of();
        }

        List<String> violations = new ArrayList<>();
        validateJwtSecret(violations);
        validateDdlAuto(violations);
        validateStorage(violations);
        validateCors(violations);
        validateApiDocumentation(violations);
        return violations;
    }

    private boolean isProductionEnvironment() {
        String value = property("app.environment", property("APP_ENV", "local"));
        return PRODUCTION_ENVIRONMENTS.contains(normalize(value));
    }

    private void validateJwtSecret(List<String> violations) {
        String secret = jwtProperties.secret();
        String normalizedSecret = normalize(secret);
        if (DEFAULT_DEV_JWT_SECRET.equals(secret)
                || normalizedSecret.contains("change-me")
                || normalizedSecret.contains("local-dev")) {
            violations.add("app.jwt.secret must be a production secret and not the local development default");
        }
    }

    private void validateDdlAuto(List<String> violations) {
        String ddlAuto = normalize(property("spring.jpa.hibernate.ddl-auto", ""));
        if (UNSAFE_DDL_AUTO_VALUES.contains(ddlAuto)) {
            violations.add("spring.jpa.hibernate.ddl-auto must not be '" + ddlAuto + "' in production");
        }
    }

    private void validateStorage(List<String> violations) {
        FileStorageProperties.Provider provider = storageProperties.getProvider();
        if (provider == null) {
            violations.add("app.storage.provider must be configured in production");
            return;
        }
        if (provider == FileStorageProperties.Provider.LOCAL) {
            violations.add("app.storage.provider must not be local in production; use durable object storage");
            return;
        }

        if (provider == FileStorageProperties.Provider.R2) {
            FileStorageProperties.R2Properties r2 = storageProperties.getR2();
            requirePresent(violations, "app.storage.r2.endpoint", r2.getEndpoint());
            requirePresent(violations, "app.storage.r2.bucket", r2.getBucket());
            requirePresent(violations, "app.storage.r2.access-key", r2.getAccessKey());
            requirePresent(violations, "app.storage.r2.secret-key", r2.getSecretKey());
            requirePresent(violations, "app.storage.r2.public-base-url", r2.getPublicBaseUrl());
        }
    }

    private void validateCors(List<String> violations) {
        List<String> unsafeOrigins = unsafeCorsValues(corsProperties.allowedOrigins());
        List<String> unsafePatterns = unsafeCorsValues(corsProperties.allowedOriginPatterns());
        if (!unsafeOrigins.isEmpty()) {
            violations.add("app.security.cors.allowed-origins must not include development or wildcard origins in production: "
                    + String.join(",", unsafeOrigins));
        }
        if (!unsafePatterns.isEmpty()) {
            violations.add("app.security.cors.allowed-origin-patterns must not include development or wildcard origins in production: "
                    + String.join(",", unsafePatterns));
        }
    }

    private void validateApiDocumentation(List<String> violations) {
        if (booleanProperty("springdoc.api-docs.enabled", true)) {
            violations.add("springdoc.api-docs.enabled must be false in production");
        }
        if (booleanProperty("springdoc.swagger-ui.enabled", true)) {
            violations.add("springdoc.swagger-ui.enabled must be false in production");
        }
    }

    private List<String> unsafeCorsValues(List<String> values) {
        return values.stream()
                .filter(this::isUnsafeCorsValue)
                .toList();
    }

    private boolean isUnsafeCorsValue(String value) {
        String normalized = normalize(value);
        return "*".equals(normalized)
                || normalized.contains("localhost")
                || normalized.contains("127.")
                || normalized.contains("0.0.0.0")
                || normalized.contains("[::1]")
                || normalized.contains("::1");
    }

    private void requirePresent(List<String> violations, String property, String value) {
        if (value == null || value.isBlank()) {
            violations.add(property + " must be configured in production");
        }
    }

    private String property(String name, String defaultValue) {
        return environment.getProperty(name, defaultValue);
    }

    private boolean booleanProperty(String name, boolean defaultValue) {
        return environment.getProperty(name, Boolean.class, defaultValue);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}