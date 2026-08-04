package com.example.goride.payment.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FareDistanceFilterPropertiesTests {

    @Test
    void providesConservativeDefaultThresholds() {
        FareDistanceFilterProperties properties = new FareDistanceFilterProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getMinMovementMeters()).isEqualTo(5.0);
        assertThat(properties.getMaxSpeedMetersPerSecond()).isEqualTo(55.0);
        assertThat(properties.getMaxSegmentGapSeconds()).isEqualTo(30);
    }

    @Test
    void rejectsInvalidThresholds() {
        FareDistanceFilterProperties properties = new FareDistanceFilterProperties();

        assertThatThrownBy(() -> properties.setMinMovementMeters(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setMinMovementMeters(Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setMaxSpeedMetersPerSecond(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setMaxSpeedMetersPerSecond(Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setMaxSegmentGapSeconds(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsExplicitOperationalThresholds() {
        FareDistanceFilterProperties properties = new FareDistanceFilterProperties();

        properties.setEnabled(false);
        properties.setMinMovementMeters(3.5);
        properties.setMaxSpeedMetersPerSecond(45);
        properties.setMaxSegmentGapSeconds(45);

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getMinMovementMeters()).isEqualTo(3.5);
        assertThat(properties.getMaxSpeedMetersPerSecond()).isEqualTo(45);
        assertThat(properties.getMaxSegmentGapSeconds()).isEqualTo(45);
    }
}
