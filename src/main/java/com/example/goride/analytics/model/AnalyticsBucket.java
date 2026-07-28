package com.example.goride.analytics.model;

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
