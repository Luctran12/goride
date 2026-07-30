package com.example.goride.location.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ThreeWordLocationPropertiesTests {

    @Test
    void normalizesBaseUrlAndTimeout() {
        ThreeWordLocationProperties properties = new ThreeWordLocationProperties();
        properties.setBaseUrl(" https://location.example.test/root/// ");
        properties.setTimeoutSeconds(7);

        assertThat(properties.normalizedBaseUrl()).isEqualTo("https://location.example.test/root");
        assertThat(properties.timeout()).isEqualTo(Duration.ofSeconds(7));
    }

    @Test
    void rejectsBlankOrNonHttpBaseUrl() {
        ThreeWordLocationProperties properties = new ThreeWordLocationProperties();

        properties.setBaseUrl(" ");
        assertThatIllegalArgumentException().isThrownBy(properties::normalizedBaseUrl);

        properties.setBaseUrl("file:///tmp/location");
        assertThatIllegalArgumentException().isThrownBy(properties::normalizedBaseUrl);
    }

    @Test
    void rejectsNonPositiveTimeout() {
        ThreeWordLocationProperties properties = new ThreeWordLocationProperties();

        assertThatIllegalArgumentException().isThrownBy(() -> properties.setTimeoutSeconds(0));
    }

    @Test
    void isDisabledByDefault() {
        ThreeWordLocationProperties properties = new ThreeWordLocationProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getBaseUrl()).isEqualTo("http://localhost:5000");
        assertThat(properties.timeout()).isEqualTo(Duration.ofSeconds(3));
    }
}
