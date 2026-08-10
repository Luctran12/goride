package com.example.goride.analytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

@ConfigurationProperties(prefix = "app.analytics.forecast-serving")
public class DemandForecastServingProperties {
    private Set<Integer> allowedHorizonsMinutes = new LinkedHashSet<>(Set.of(15, 30, 60));
    private int maximumForecastRows = 5000;
    private int maximumHotspots = 200;
    private int maximumPageSize = 100;
    private int maximumEvaluationRows = 1000;
    private int maximumRangeDays = 31;
    private int minimumActualDemandCount = 3;
    private Duration publishedStaleAfter = Duration.ofMinutes(30);
    private Duration processingStaleAfter = Duration.ofHours(24);

    public Set<Integer> getAllowedHorizonsMinutes() {
        return Set.copyOf(allowedHorizonsMinutes);
    }

    public void setAllowedHorizonsMinutes(Set<Integer> allowedHorizonsMinutes) {
        if (allowedHorizonsMinutes == null || allowedHorizonsMinutes.isEmpty()
                || allowedHorizonsMinutes.stream().anyMatch(value -> value == null || value <= 0)) {
            throw new IllegalArgumentException(
                    "app.analytics.forecast-serving.allowed-horizons-minutes must be positive"
            );
        }
        this.allowedHorizonsMinutes = new LinkedHashSet<>(allowedHorizonsMinutes);
    }

    public int getMaximumForecastRows() {
        return maximumForecastRows;
    }

    public void setMaximumForecastRows(int maximumForecastRows) {
        this.maximumForecastRows = requirePositive(maximumForecastRows, "maximum-forecast-rows");
    }

    public int getMaximumHotspots() {
        return maximumHotspots;
    }

    public void setMaximumHotspots(int maximumHotspots) {
        this.maximumHotspots = requirePositive(maximumHotspots, "maximum-hotspots");
    }

    public int getMaximumPageSize() {
        return maximumPageSize;
    }

    public void setMaximumPageSize(int maximumPageSize) {
        this.maximumPageSize = requirePositive(maximumPageSize, "maximum-page-size");
    }

    public int getMaximumEvaluationRows() {
        return maximumEvaluationRows;
    }

    public void setMaximumEvaluationRows(int maximumEvaluationRows) {
        this.maximumEvaluationRows = requirePositive(maximumEvaluationRows, "maximum-evaluation-rows");
    }

    public int getMaximumRangeDays() {
        return maximumRangeDays;
    }

    public void setMaximumRangeDays(int maximumRangeDays) {
        this.maximumRangeDays = requirePositive(maximumRangeDays, "maximum-range-days");
    }

    public int getMinimumActualDemandCount() {
        return minimumActualDemandCount;
    }

    public void setMinimumActualDemandCount(int minimumActualDemandCount) {
        if (minimumActualDemandCount < 3) {
            throw new IllegalArgumentException(
                    "app.analytics.forecast-serving.minimum-actual-demand-count must be at least 3"
            );
        }
        this.minimumActualDemandCount = minimumActualDemandCount;
    }

    public Duration getPublishedStaleAfter() {
        return publishedStaleAfter;
    }

    public void setPublishedStaleAfter(Duration publishedStaleAfter) {
        this.publishedStaleAfter = requirePositive(publishedStaleAfter, "published-stale-after");
    }

    public Duration getProcessingStaleAfter() {
        return processingStaleAfter;
    }

    public void setProcessingStaleAfter(Duration processingStaleAfter) {
        this.processingStaleAfter = requirePositive(processingStaleAfter, "processing-stale-after");
    }

    private int requirePositive(int value, String property) {
        if (value <= 0) {
            throw new IllegalArgumentException(
                    "app.analytics.forecast-serving." + property + " must be positive"
            );
        }
        return value;
    }

    private Duration requirePositive(Duration value, String property) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(
                    "app.analytics.forecast-serving." + property + " must be positive"
            );
        }
        return value;
    }
}
