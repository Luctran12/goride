package com.example.goride.analytics.config;

import com.example.goride.analytics.model.AnalyticsSourceVariant;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.analytics.materialized")
public class AnalyticsMaterializedProperties {
    private boolean enabled;
    private AnalyticsSourceVariant queryVariant = AnalyticsSourceVariant.DIRECT;
    private boolean fallbackEnabled = true;
    private String reportingTimezone = "Asia/Ho_Chi_Minh";
    private int projectedSrid = 32648;
    private int baseCellSizeMeters = 250;
    private boolean refreshEnabled;
    private long refreshFixedDelayMs = 900_000;
    private long refreshInitialDelayMs = 60_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public AnalyticsSourceVariant getQueryVariant() {
        return queryVariant;
    }

    public void setQueryVariant(AnalyticsSourceVariant queryVariant) {
        if (queryVariant == null) {
            throw new IllegalArgumentException(
                    "app.analytics.materialized.query-variant is required"
            );
        }
        this.queryVariant = queryVariant;
    }

    public boolean isFallbackEnabled() {
        return fallbackEnabled;
    }

    public void setFallbackEnabled(boolean fallbackEnabled) {
        this.fallbackEnabled = fallbackEnabled;
    }

    public String getReportingTimezone() {
        return reportingTimezone;
    }

    public void setReportingTimezone(String reportingTimezone) {
        if (reportingTimezone == null || reportingTimezone.isBlank()) {
            throw new IllegalArgumentException(
                    "app.analytics.materialized.reporting-timezone is required"
            );
        }
        this.reportingTimezone = reportingTimezone.trim();
    }

    public int getProjectedSrid() {
        return projectedSrid;
    }

    public void setProjectedSrid(int projectedSrid) {
        if (projectedSrid <= 0) {
            throw new IllegalArgumentException(
                    "app.analytics.materialized.projected-srid must be positive"
            );
        }
        this.projectedSrid = projectedSrid;
    }

    public int getBaseCellSizeMeters() {
        return baseCellSizeMeters;
    }

    public void setBaseCellSizeMeters(int baseCellSizeMeters) {
        if (baseCellSizeMeters <= 0) {
            throw new IllegalArgumentException(
                    "app.analytics.materialized.base-cell-size-meters must be positive"
            );
        }
        this.baseCellSizeMeters = baseCellSizeMeters;
    }

    public boolean isRefreshEnabled() {
        return refreshEnabled;
    }

    public void setRefreshEnabled(boolean refreshEnabled) {
        this.refreshEnabled = refreshEnabled;
    }

    public long getRefreshFixedDelayMs() {
        return refreshFixedDelayMs;
    }

    public void setRefreshFixedDelayMs(long refreshFixedDelayMs) {
        if (refreshFixedDelayMs < 1_000) {
            throw new IllegalArgumentException(
                    "app.analytics.materialized.refresh-fixed-delay-ms must be at least 1000"
            );
        }
        this.refreshFixedDelayMs = refreshFixedDelayMs;
    }

    public long getRefreshInitialDelayMs() {
        return refreshInitialDelayMs;
    }

    public void setRefreshInitialDelayMs(long refreshInitialDelayMs) {
        if (refreshInitialDelayMs < 0) {
            throw new IllegalArgumentException(
                    "app.analytics.materialized.refresh-initial-delay-ms must not be negative"
            );
        }
        this.refreshInitialDelayMs = refreshInitialDelayMs;
    }
}
