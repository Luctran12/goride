package com.example.goride.analytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;

@ConfigurationProperties(prefix = "app.analytics.spatial")
public class AnalyticsSpatialProperties {
    private Set<Integer> allowedCellSizesMeters = new LinkedHashSet<>(
            Set.of(250, 500, 1000, 2000)
    );
    private int projectedSrid = 32648;
    private int maximumRangeDays = 31;
    private int maximumCells = 5000;
    private double maximumBoundingBoxAreaSquareKm = 25_000;

    public Set<Integer> getAllowedCellSizesMeters() {
        return Set.copyOf(allowedCellSizesMeters);
    }

    public void setAllowedCellSizesMeters(Set<Integer> allowedCellSizesMeters) {
        if (allowedCellSizesMeters == null
                || allowedCellSizesMeters.isEmpty()
                || allowedCellSizesMeters.stream().anyMatch(size -> size == null || size <= 0)) {
            throw new IllegalArgumentException(
                    "app.analytics.spatial.allowed-cell-sizes-meters must contain positive values"
            );
        }
        this.allowedCellSizesMeters = new LinkedHashSet<>(allowedCellSizesMeters);
    }

    public int getProjectedSrid() {
        return projectedSrid;
    }

    public void setProjectedSrid(int projectedSrid) {
        if (projectedSrid <= 0) {
            throw new IllegalArgumentException(
                    "app.analytics.spatial.projected-srid must be positive"
            );
        }
        this.projectedSrid = projectedSrid;
    }

    public int getMaximumRangeDays() {
        return maximumRangeDays;
    }

    public void setMaximumRangeDays(int maximumRangeDays) {
        if (maximumRangeDays <= 0) {
            throw new IllegalArgumentException(
                    "app.analytics.spatial.maximum-range-days must be positive"
            );
        }
        this.maximumRangeDays = maximumRangeDays;
    }

    public int getMaximumCells() {
        return maximumCells;
    }

    public void setMaximumCells(int maximumCells) {
        if (maximumCells <= 0) {
            throw new IllegalArgumentException(
                    "app.analytics.spatial.maximum-cells must be positive"
            );
        }
        this.maximumCells = maximumCells;
    }

    public double getMaximumBoundingBoxAreaSquareKm() {
        return maximumBoundingBoxAreaSquareKm;
    }

    public void setMaximumBoundingBoxAreaSquareKm(double maximumBoundingBoxAreaSquareKm) {
        if (!Double.isFinite(maximumBoundingBoxAreaSquareKm)
                || maximumBoundingBoxAreaSquareKm <= 0) {
            throw new IllegalArgumentException(
                    "app.analytics.spatial.maximum-bounding-box-area-square-km must be positive"
            );
        }
        this.maximumBoundingBoxAreaSquareKm = maximumBoundingBoxAreaSquareKm;
    }
}
