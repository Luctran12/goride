package com.example.goride.driver.service.availability;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.driver.availability")
public class DriverAvailabilityProperties {
    private long heartbeatTimeoutSeconds = 60;
    private int cleanupBatchSize = 100;

    public long getHeartbeatTimeoutSeconds() {
        return heartbeatTimeoutSeconds;
    }

    public void setHeartbeatTimeoutSeconds(long heartbeatTimeoutSeconds) {
        if (heartbeatTimeoutSeconds < 10) {
            throw new IllegalArgumentException("heartbeatTimeoutSeconds must be at least 10");
        }
        this.heartbeatTimeoutSeconds = heartbeatTimeoutSeconds;
    }

    public int getCleanupBatchSize() {
        return cleanupBatchSize;
    }

    public void setCleanupBatchSize(int cleanupBatchSize) {
        if (cleanupBatchSize <= 0) {
            throw new IllegalArgumentException("cleanupBatchSize must be positive");
        }
        this.cleanupBatchSize = cleanupBatchSize;
    }

    public Duration heartbeatTimeout() {
        return Duration.ofSeconds(heartbeatTimeoutSeconds);
    }
}
