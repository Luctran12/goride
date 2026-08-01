package com.example.goride.analytics.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        description = "Calendar bucket in the requested reporting timezone. "
                + "Demand supports HOUR, DAY and WEEK; supply supports HOUR and DAY."
)
public enum AnalyticsBucket {
    HOUR("hour"),
    DAY("day"),
    WEEK("week");

    private final String sqlUnit;

    AnalyticsBucket(String sqlUnit) {
        this.sqlUnit = sqlUnit;
    }

    public String sqlUnit() {
        return sqlUnit;
    }

    public boolean supportsSupply() {
        return this == HOUR || this == DAY;
    }
}
