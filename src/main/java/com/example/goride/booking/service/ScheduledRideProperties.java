package com.example.goride.booking.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.booking.scheduled-rides")
public class ScheduledRideProperties {
    private boolean enabled = true;
    private long minLeadTimeMinutes = 15;
    private long dispatchLeadTimeMinutes = 10;
    private int dispatchBatchSize = 50;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getMinLeadTimeMinutes() {
        return minLeadTimeMinutes;
    }

    public void setMinLeadTimeMinutes(long minLeadTimeMinutes) {
        if (minLeadTimeMinutes <= 0) {
            throw new IllegalArgumentException("minLeadTimeMinutes must be positive");
        }
        this.minLeadTimeMinutes = minLeadTimeMinutes;
    }

    public long getDispatchLeadTimeMinutes() {
        return dispatchLeadTimeMinutes;
    }

    public void setDispatchLeadTimeMinutes(long dispatchLeadTimeMinutes) {
        if (dispatchLeadTimeMinutes < 0) {
            throw new IllegalArgumentException("dispatchLeadTimeMinutes must not be negative");
        }
        this.dispatchLeadTimeMinutes = dispatchLeadTimeMinutes;
    }

    public int getDispatchBatchSize() {
        return dispatchBatchSize;
    }

    public void setDispatchBatchSize(int dispatchBatchSize) {
        if (dispatchBatchSize <= 0) {
            throw new IllegalArgumentException("dispatchBatchSize must be positive");
        }
        this.dispatchBatchSize = dispatchBatchSize;
    }

    public Duration minLeadTime() {
        return Duration.ofMinutes(minLeadTimeMinutes);
    }

    public Duration dispatchLeadTime() {
        return Duration.ofMinutes(dispatchLeadTimeMinutes);
    }
}