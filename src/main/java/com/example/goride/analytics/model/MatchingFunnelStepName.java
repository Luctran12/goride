package com.example.goride.analytics.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Stable matching-funnel step identifier in display order.")
public enum MatchingFunnelStepName {
    RUN_STARTED,
    CANDIDATE_FOUND,
    OFFER_SENT,
    OFFER_ACCEPTED,
    TRIP_COMPLETED
}
