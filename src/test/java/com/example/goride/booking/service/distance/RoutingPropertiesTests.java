package com.example.goride.booking.service.distance;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutingPropertiesTests {

    @Test
    void exposesSafeFallbackDefaults() {
        RoutingProperties properties = new RoutingProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.normalizedBaseUrl()).isEqualTo("https://router.project-osrm.org");
        assertThat(properties.normalizedProfile()).isEqualTo("driving");
        assertThat(properties.timeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.isFallbackEnabled()).isTrue();
    }

    @Test
    void normalizesConfiguredValues() {
        RoutingProperties properties = new RoutingProperties();
        properties.setBaseUrl(" https://routing.goride.test/ ");
        properties.setProfile(" bike ");
        properties.setTimeoutSeconds(12);
        properties.setFallbackEnabled(false);

        assertThat(properties.normalizedBaseUrl()).isEqualTo("https://routing.goride.test");
        assertThat(properties.normalizedProfile()).isEqualTo("bike");
        assertThat(properties.timeout()).isEqualTo(Duration.ofSeconds(12));
        assertThat(properties.isFallbackEnabled()).isFalse();
    }

    @Test
    void rejectsInvalidProviderConfiguration() {
        RoutingProperties properties = new RoutingProperties();

        assertThatThrownBy(() -> properties.setTimeoutSeconds(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("timeoutSeconds must be positive");

        properties.setBaseUrl("file:///tmp/routes");
        assertThatThrownBy(properties::normalizedBaseUrl)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Routing base URL must be an HTTP URL");

        properties.setProfile("../driving");
        assertThatThrownBy(properties::normalizedProfile)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Routing profile is invalid");
    }
}
