package com.example.goride.analytics.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Physical data source used to answer an analytics request.")
public enum AnalyticsSourceVariant {
    DIRECT,
    MATERIALIZED
}
