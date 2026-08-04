package com.example.goride.booking.service.distance;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutingEstimateCachePropertiesTests {

    @Test
    void providesSharedCacheDefaults() {
        RoutingEstimateCacheProperties properties = new RoutingEstimateCacheProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getCoordinateScale()).isEqualTo(4);
        assertThat(properties.ttl()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void acceptsOperationalOverrides() {
        RoutingEstimateCacheProperties properties = new RoutingEstimateCacheProperties();

        properties.setEnabled(false);
        properties.setCoordinateScale(5);
        properties.setTtlSeconds(120);

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getCoordinateScale()).isEqualTo(5);
        assertThat(properties.ttl()).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    void rejectsUnsafeValues() {
        RoutingEstimateCacheProperties properties = new RoutingEstimateCacheProperties();

        assertThatThrownBy(() -> properties.setCoordinateScale(2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setCoordinateScale(7))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setTtlSeconds(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
