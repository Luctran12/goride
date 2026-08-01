package com.example.goride.analytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.analytics.telemetry")
public class AnalyticsTelemetryProperties {
    private boolean supplySnapshotEnabled = true;
    private long supplySnapshotIntervalSeconds = 300;

    public boolean isSupplySnapshotEnabled() {
        return supplySnapshotEnabled;
    }

    public void setSupplySnapshotEnabled(boolean supplySnapshotEnabled) {
        this.supplySnapshotEnabled = supplySnapshotEnabled;
    }

    public long getSupplySnapshotIntervalSeconds() {
        return supplySnapshotIntervalSeconds;
    }

    public void setSupplySnapshotIntervalSeconds(long supplySnapshotIntervalSeconds) {
        if (supplySnapshotIntervalSeconds < 60) {
            throw new IllegalArgumentException(
                    "app.analytics.telemetry.supply-snapshot-interval-seconds must be at least 60"
            );
        }
        this.supplySnapshotIntervalSeconds = supplySnapshotIntervalSeconds;
    }
}
