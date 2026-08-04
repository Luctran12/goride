package com.example.goride.booking.service.distance;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.routing.estimate-cache")
public class RoutingEstimateCacheProperties {
    private boolean enabled = true;
    private int coordinateScale = 4;
    private long ttlSeconds = 300;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getCoordinateScale() {
        return coordinateScale;
    }

    public void setCoordinateScale(int coordinateScale) {
        if (coordinateScale < 3 || coordinateScale > 6) {
            throw new IllegalArgumentException("coordinateScale must be between 3 and 6");
        }
        this.coordinateScale = coordinateScale;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(long ttlSeconds) {
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("ttlSeconds must be positive");
        }
        this.ttlSeconds = ttlSeconds;
    }

    public Duration ttl() {
        return Duration.ofSeconds(ttlSeconds);
    }
}
