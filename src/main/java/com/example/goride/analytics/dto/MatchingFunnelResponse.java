package com.example.goride.analytics.dto;

import com.example.goride.analytics.model.AnalyticsCountUnit;
import com.example.goride.analytics.model.AnalyticsSourceVariant;
import com.example.goride.analytics.model.MatchingFunnelStepName;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(
        name = "MatchingFunnel",
        description = "Five stable, ordered funnel steps. The first four count distinct runs; "
                + "the final step counts distinct completed trips.",
        requiredProperties = {
                "from", "to", "reportingTimezone", "sourceVariant", "dataFreshnessAt", "steps"
        },
        example = """
                {
                  "from": "2026-06-30T17:00:00Z",
                  "to": "2026-07-07T17:00:00Z",
                  "reportingTimezone": "Asia/Ho_Chi_Minh",
                  "sourceVariant": "DIRECT",
                  "dataFreshnessAt": "2026-07-07T17:00:00Z",
                  "steps": [
                    {"name": "RUN_STARTED", "unit": "RUN", "count": 1000},
                    {"name": "CANDIDATE_FOUND", "unit": "RUN", "count": 960},
                    {"name": "OFFER_SENT", "unit": "RUN", "count": 940},
                    {"name": "OFFER_ACCEPTED", "unit": "RUN", "count": 900},
                    {"name": "TRIP_COMPLETED", "unit": "TRIP", "count": 820}
                  ]
                }
                """
)
public record MatchingFunnelResponse(
        @Schema(description = "Inclusive normalized UTC range boundary.")
        Instant from,
        @Schema(description = "Exclusive normalized UTC range boundary.")
        Instant to,
        @Schema(description = "IANA timezone used for calendar semantics.")
        String reportingTimezone,
        @Schema(description = "Physical query source used for this response.")
        AnalyticsSourceVariant sourceVariant,
        @Schema(description = "Database snapshot cutoff represented by the response.")
        Instant dataFreshnessAt,
        @Schema(
                description = "Always contains all five steps in semantic display order, including "
                        + "zero-count steps."
        )
        List<Step> steps
) {
    @Schema(
            name = "MatchingFunnelStep",
            description = "One stable matching-funnel step.",
            requiredProperties = {"name", "unit", "count"}
    )
    public record Step(
            @Schema(description = "Machine-readable stable step identifier.")
            MatchingFunnelStepName name,
            @Schema(description = "Count unit; do not compare RUN and TRIP as one cohort.")
            AnalyticsCountUnit unit,
            @Schema(description = "Number of distinct units at this step.", example = "1000")
            long count
    ) {
    }
}
