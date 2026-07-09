package com.example.goride.common.config;

import com.example.goride.auth.config.CorsProperties;
import com.example.goride.auth.config.JwtProperties;
import com.example.goride.storage.config.FileStorageProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionReadinessValidatorTests {
    @Test
    void skipsChecksOutsideProduction() {
        ProductionReadinessValidator validator = new ProductionReadinessValidator(
                environment("local", "update"),
                new JwtProperties("local-dev-secret-change-me-please-change", 15, 7),
                cors(List.of("http://localhost:5173"), List.of("*")),
                new FileStorageProperties()
        );

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void acceptsProductionSafeConfiguration() {
        ProductionReadinessValidator validator = new ProductionReadinessValidator(
                environment("production", "validate"),
                productionJwt(),
                cors(List.of("https://app.goride.example"), List.of()),
                r2Storage()
        );

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void rejectsDevelopmentDefaultsInProduction() {
        ProductionReadinessValidator validator = new ProductionReadinessValidator(
                environment("production", "update"),
                new JwtProperties("local-dev-secret-change-me-please-change", 15, 7),
                cors(List.of("http://localhost:5173", "https://app.goride.example"), List.of("*")),
                new FileStorageProperties()
        );

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production readiness check failed")
                .hasMessageContaining("app.jwt.secret")
                .hasMessageContaining("spring.jpa.hibernate.ddl-auto")
                .hasMessageContaining("app.storage.provider")
                .hasMessageContaining("app.security.cors.allowed-origins")
                .hasMessageContaining("app.security.cors.allowed-origin-patterns");
    }

    @Test
    void rejectsIncompleteR2ConfigurationInProduction() {
        FileStorageProperties storage = new FileStorageProperties();
        storage.setProvider(FileStorageProperties.Provider.R2);

        ProductionReadinessValidator validator = new ProductionReadinessValidator(
                environment("prod", "none"),
                productionJwt(),
                cors(List.of("https://app.goride.example"), List.of()),
                storage
        );

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.storage.r2.endpoint")
                .hasMessageContaining("app.storage.r2.bucket")
                .hasMessageContaining("app.storage.r2.access-key")
                .hasMessageContaining("app.storage.r2.secret-key")
                .hasMessageContaining("app.storage.r2.public-base-url");
    }

    @Test
    void rejectsMissingStorageProviderInProduction() {
        FileStorageProperties storage = new FileStorageProperties();
        storage.setProvider(null);

        ProductionReadinessValidator validator = new ProductionReadinessValidator(
                environment("production", "validate"),
                productionJwt(),
                cors(List.of("https://app.goride.example"), List.of()),
                storage
        );

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.storage.provider must be configured in production");
    }
    @Test
    void rejectsLoopbackCorsOriginsInProduction() {
        ProductionReadinessValidator validator = new ProductionReadinessValidator(
                environment("production", "validate"),
                productionJwt(),
                cors(List.of("http://127.0.0.2:5173"), List.of("http://[::1]:3000")),
                r2Storage()
        );

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.security.cors.allowed-origins")
                .hasMessageContaining("app.security.cors.allowed-origin-patterns");
    }
    private MockEnvironment environment(String appEnvironment, String ddlAuto) {
        return new MockEnvironment()
                .withProperty("app.environment", appEnvironment)
                .withProperty("spring.jpa.hibernate.ddl-auto", ddlAuto);
    }

    private JwtProperties productionJwt() {
        return new JwtProperties("prod-secret-at-least-32-bytes-long", 15, 7);
    }

    private CorsProperties cors(List<String> allowedOrigins, List<String> allowedOriginPatterns) {
        return new CorsProperties(
                allowedOrigins,
                allowedOriginPatterns,
                List.of("GET", "POST"),
                List.of("Authorization", "Content-Type"),
                List.of("X-Request-Id"),
                false,
                3600
        );
    }

    private FileStorageProperties r2Storage() {
        FileStorageProperties storage = new FileStorageProperties();
        storage.setProvider(FileStorageProperties.Provider.R2);
        storage.getR2().setEndpoint("https://example.r2.cloudflarestorage.com");
        storage.getR2().setBucket("goride-prod");
        storage.getR2().setAccessKey("access-key");
        storage.getR2().setSecretKey("secret-key");
        storage.getR2().setPublicBaseUrl("https://cdn.goride.example");
        return storage;
    }
}