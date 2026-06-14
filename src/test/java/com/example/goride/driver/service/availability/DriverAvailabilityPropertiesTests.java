package com.example.goride.driver.service.availability;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DriverAvailabilityPropertiesTests {
    @Test
    void usesSafeDefaults() {
        DriverAvailabilityProperties properties = new DriverAvailabilityProperties();

        assertThat(properties.heartbeatTimeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(properties.getCleanupBatchSize()).isEqualTo(100);
    }

    @Test
    void rejectsTooShortHeartbeatTimeout() {
        DriverAvailabilityProperties properties = new DriverAvailabilityProperties();

        assertThatThrownBy(() -> properties.setHeartbeatTimeoutSeconds(9))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 10");
    }

    @Test
    void rejectsNonPositiveCleanupBatchSize() {
        DriverAvailabilityProperties properties = new DriverAvailabilityProperties();

        assertThatThrownBy(() -> properties.setCleanupBatchSize(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }
}
