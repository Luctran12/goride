package com.example.goride.common.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeConfigurationSecurityTests {
    @Test
    void datasourceConfigurationUsesEnvironmentOverridesAndLocalOnlyDefaults()
            throws IOException {
        String applicationYaml = Files.readString(
                Path.of("src", "main", "resources", "application.yml")
        );

        assertThat(applicationYaml)
                .contains("url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/goride}")
                .contains("username: ${DATABASE_USERNAME:goride}")
                .contains("password: ${DATABASE_PASSWORD:goride}")
                .doesNotContain("supabase.co");
    }
}
