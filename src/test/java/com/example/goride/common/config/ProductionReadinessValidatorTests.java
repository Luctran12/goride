package com.example.goride.common.config;

import com.example.goride.auth.config.CorsProperties;
import com.example.goride.auth.config.JwtProperties;
import com.example.goride.storage.config.FileStorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
    void allowsApiDocumentationOutsideProduction() {
        ProductionReadinessValidator validator = new ProductionReadinessValidator(
                environmentWithApiDocs("staging", "update", true, true),
                new JwtProperties("local-dev-secret-change-me-please-change", 15, 7),
                cors(List.of("http://localhost:5173"), List.of("*")),
                new FileStorageProperties()
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
    void rejectsLoopbackSuperuserAndDefaultDatabasePasswordInProduction() {
        MockEnvironment environment = environment("production", "validate")
                .withProperty(
                        "spring.datasource.url",
                        "jdbc:postgresql://localhost:5432/goride"
                )
                .withProperty("spring.datasource.username", "postgres")
                .withProperty("spring.datasource.password", "goride");

        ProductionReadinessValidator validator = new ProductionReadinessValidator(
                environment,
                productionJwt(),
                cors(List.of("https://app.goride.example"), List.of()),
                r2Storage()
        );

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.url")
                .hasMessageContaining("spring.datasource.username")
                .hasMessageContaining("spring.datasource.password");
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

    @ParameterizedTest
    @CsvSource({
            "true, false, springdoc.api-docs.enabled",
            "false, true, springdoc.swagger-ui.enabled"
    })
    void rejectsAnyEnabledApiDocumentationSurfaceInProduction(
            boolean apiDocsEnabled,
            boolean swaggerUiEnabled,
            String expectedViolation
    ) {
        ProductionReadinessValidator validator = new ProductionReadinessValidator(
                environmentWithApiDocs("production", "validate", apiDocsEnabled, swaggerUiEnabled),
                productionJwt(),
                cors(List.of("https://app.goride.example"), List.of()),
                r2Storage()
        );

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(expectedViolation);
    }

    @Test
    void rejectsInMemoryRateLimitStoreInProduction() {
        MockEnvironment environment = environment("production", "validate")
                .withProperty("app.security.rate-limit.store", "memory");

        ProductionReadinessValidator validator = new ProductionReadinessValidator(
                environment,
                productionJwt(),
                cors(List.of("https://app.goride.example"), List.of()),
                r2Storage()
        );

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.security.rate-limit.store must be redis");
    }

    @Test
    void allowsInMemoryStoreWhenProductionRateLimitingIsDisabled() {
        MockEnvironment environment = environment("production", "validate")
                .withProperty("app.security.rate-limit.enabled", "false")
                .withProperty("app.security.rate-limit.store", "memory");

        ProductionReadinessValidator validator = new ProductionReadinessValidator(
                environment,
                productionJwt(),
                cors(List.of("https://app.goride.example"), List.of()),
                r2Storage()
        );

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void rejectsDisabledOrLoopbackTracingInProduction() {
        MockEnvironment environment = environment("production", "validate")
                .withProperty("management.tracing.enabled", "false")
                .withProperty("management.otlp.tracing.export.enabled", "false")
                .withProperty("management.otlp.tracing.endpoint", "http://localhost:4318/v1/traces");

        ProductionReadinessValidator validator = new ProductionReadinessValidator(
                environment,
                productionJwt(),
                cors(List.of("https://app.goride.example"), List.of()),
                r2Storage()
        );

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("management.tracing.enabled must be true")
                .hasMessageContaining("management.otlp.tracing.export.enabled must be true")
                .hasMessageContaining("management.otlp.tracing.endpoint must be an absolute non-loopback");
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.1", "0", "1.1", "NaN"})
    void rejectsUnsafeTracingSamplingProbability(String samplingProbability) {
        MockEnvironment environment = environment("production", "validate")
                .withProperty("management.tracing.sampling.probability", samplingProbability);

        ProductionReadinessValidator validator = new ProductionReadinessValidator(
                environment,
                productionJwt(),
                cors(List.of("https://app.goride.example"), List.of()),
                r2Storage()
        );

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("management.tracing.sampling.probability must be greater than 0");
    }

    @Test
    void allowsExplicitProductionTracingOptOutForExternalAgent() {
        MockEnvironment environment = environment("production", "validate")
                .withProperty("app.observability.tracing.required-in-production", "false")
                .withProperty("management.tracing.enabled", "false")
                .withProperty("management.otlp.tracing.export.enabled", "false")
                .withProperty("management.otlp.tracing.endpoint", "http://localhost:4318/v1/traces")
                .withProperty("management.tracing.sampling.probability", "0");

        ProductionReadinessValidator validator = new ProductionReadinessValidator(
                environment,
                productionJwt(),
                cors(List.of("https://app.goride.example"), List.of()),
                r2Storage()
        );

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    private MockEnvironment environment(String appEnvironment, String ddlAuto) {
        return environmentWithApiDocs(appEnvironment, ddlAuto, false, false);
    }

    private MockEnvironment environmentWithApiDocs(
            String appEnvironment,
            String ddlAuto,
            boolean apiDocsEnabled,
            boolean swaggerUiEnabled
    ) {
        return new MockEnvironment()
                .withProperty("app.environment", appEnvironment)
                .withProperty(
                        "spring.datasource.url",
                        "jdbc:postgresql://postgres.internal:5432/goride"
                )
                .withProperty("spring.datasource.username", "goride_app")
                .withProperty("spring.datasource.password", "prod-db-secret")
                .withProperty("spring.jpa.hibernate.ddl-auto", ddlAuto)
                .withProperty("springdoc.api-docs.enabled", Boolean.toString(apiDocsEnabled))
                .withProperty("springdoc.swagger-ui.enabled", Boolean.toString(swaggerUiEnabled))
                .withProperty("app.security.rate-limit.enabled", "true")
                .withProperty("app.security.rate-limit.store", "redis")
                .withProperty("app.observability.tracing.required-in-production", "true")
                .withProperty("management.tracing.enabled", "true")
                .withProperty("management.otlp.tracing.export.enabled", "true")
                .withProperty(
                        "management.otlp.tracing.endpoint",
                        "http://otel-collector:4318/v1/traces"
                )
                .withProperty("management.tracing.sampling.probability", "0.1");
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
