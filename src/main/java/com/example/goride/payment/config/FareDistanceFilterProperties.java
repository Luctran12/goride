package com.example.goride.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payment.actual-fare.gps-filter")
public class FareDistanceFilterProperties {
    private boolean enabled = true;
    private double minMovementMeters = 5.0;
    private double maxSpeedMetersPerSecond = 55.0;
    private long maxSegmentGapSeconds = 30;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public double getMinMovementMeters() {
        return minMovementMeters;
    }

    public void setMinMovementMeters(double minMovementMeters) {
        this.minMovementMeters = requireNonNegative(minMovementMeters, "minMovementMeters");
    }

    public double getMaxSpeedMetersPerSecond() {
        return maxSpeedMetersPerSecond;
    }

    public void setMaxSpeedMetersPerSecond(double maxSpeedMetersPerSecond) {
        this.maxSpeedMetersPerSecond = requirePositive(maxSpeedMetersPerSecond, "maxSpeedMetersPerSecond");
    }

    public long getMaxSegmentGapSeconds() {
        return maxSegmentGapSeconds;
    }

    public void setMaxSegmentGapSeconds(long maxSegmentGapSeconds) {
        if (maxSegmentGapSeconds <= 0) {
            throw new IllegalArgumentException("maxSegmentGapSeconds must be positive");
        }
        this.maxSegmentGapSeconds = maxSegmentGapSeconds;
    }

    private double requireNonNegative(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(fieldName + " must be finite and non-negative");
        }
        return value;
    }

    private double requirePositive(double value, String fieldName) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be finite and positive");
        }
        return value;
    }
}
