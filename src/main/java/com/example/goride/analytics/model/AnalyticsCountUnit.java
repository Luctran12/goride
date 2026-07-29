package com.example.goride.analytics.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Unit used by a matching-funnel step.")
public enum AnalyticsCountUnit {
    RUN,
    TRIP
}
