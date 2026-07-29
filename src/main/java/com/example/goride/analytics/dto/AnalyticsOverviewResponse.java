package com.example.goride.analytics.dto;

import com.example.goride.analytics.model.AnalyticsSourceVariant;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(
        name = "AnalyticsOverview",
        description = "Normalized trip, completed-payment and matching KPIs. "
                + "Ratios use scale 4; duration values are rounded to whole milliseconds.",
        requiredProperties = {
                "from", "to", "reportingTimezone", "sourceVariant", "dataFreshnessAt",
                "tripRequests", "completedTrips", "completedTripsByRequestCohort",
                "cancelledTrips", "noDriverTrips", "completionRate", "completedPayments",
                "completedRevenue", "matchingRuns", "terminalRuns", "matchingSuccessRate",
                "averageMatchingDurationMs", "p50MatchingDurationMs", "p95MatchingDurationMs"
        },
        example = """
                {
                  "from": "2026-06-30T17:00:00Z",
                  "to": "2026-07-31T17:00:00Z",
                  "reportingTimezone": "Asia/Ho_Chi_Minh",
                  "sourceVariant": "DIRECT",
                  "dataFreshnessAt": "2026-07-31T17:00:00Z",
                  "tripRequests": 10000,
                  "completedTrips": 8250,
                  "completedTripsByRequestCohort": 8200,
                  "cancelledTrips": 900,
                  "noDriverTrips": 50,
                  "completionRate": 0.8200,
                  "completedPayments": 8150,
                  "completedRevenue": 245000000,
                  "matchingRuns": 10000,
                  "terminalRuns": 9980,
                  "matchingSuccessRate": 0.9100,
                  "averageMatchingDurationMs": 10320,
                  "p50MatchingDurationMs": 8200,
                  "p95MatchingDurationMs": 26400
                }
                """
)
public record AnalyticsOverviewResponse(
        @Schema(
                description = "Inclusive normalized UTC range boundary.",
                example = "2026-06-30T17:00:00Z"
        )
        Instant from,
        @Schema(
                description = "Exclusive normalized UTC range boundary.",
                example = "2026-07-31T17:00:00Z"
        )
        Instant to,
        @Schema(
                description = "IANA timezone used for calendar semantics.",
                example = "Asia/Ho_Chi_Minh"
        )
        String reportingTimezone,
        @Schema(description = "Physical query source used for this response.")
        AnalyticsSourceVariant sourceVariant,
        @Schema(
                description = "Database snapshot cutoff represented by the response.",
                example = "2026-07-31T17:00:00Z"
        )
        Instant dataFreshnessAt,
        @Schema(description = "Trips requested in [from, to). Unit: trips.", example = "10000")
        long tripRequests,
        @Schema(
                description = "Trips completed in [from, to) by completion time. Unit: trips.",
                example = "8250"
        )
        long completedTrips,
        @Schema(
                description = "Trips requested in [from, to) whose current outcome is COMPLETED. "
                        + "Unit: trips.",
                example = "8200"
        )
        long completedTripsByRequestCohort,
        @Schema(
                description = "Trips cancelled in [from, to) by cancellation time. Unit: trips.",
                example = "900"
        )
        long cancelledTrips,
        @Schema(
                description = "Trips entering terminal NO_DRIVER in [from, to). Unit: trips.",
                example = "50"
        )
        long noDriverTrips,
        @Schema(
                description = "completedTripsByRequestCohort / tripRequests. Unit: ratio; "
                        + "scale: 4; null when tripRequests is zero.",
                example = "0.8200",
                minimum = "0",
                maximum = "1",
                types = {"number", "null"},
                nullable = true
        )
        BigDecimal completionRate,
        @Schema(
                description = "Completed payments recognized in [from, to). Unit: payments.",
                example = "8150"
        )
        long completedPayments,
        @Schema(
                description = "Sum of completed payment amounts. Unit: current payment currency "
                        + "(VND in the current deployment).",
                example = "245000000"
        )
        BigDecimal completedRevenue,
        @Schema(description = "Matching runs started in [from, to). Unit: runs.", example = "10000")
        long matchingRuns,
        @Schema(
                description = "Matching runs reaching a terminal outcome in [from, to). "
                        + "Unit: runs.",
                example = "9980"
        )
        long terminalRuns,
        @Schema(
                description = "matchedRuns / terminalRuns. Unit: ratio; scale: 4; "
                        + "null when terminalRuns is zero.",
                example = "0.9100",
                minimum = "0",
                maximum = "1",
                types = {"number", "null"},
                nullable = true
        )
        BigDecimal matchingSuccessRate,
        @Schema(
                description = "Mean terminal matching duration. Unit: milliseconds; "
                        + "null when no terminal run exists.",
                example = "10320",
                format = "int64",
                types = {"integer", "null"},
                nullable = true
        )
        Long averageMatchingDurationMs,
        @Schema(
                description = "Continuous P50 terminal matching duration. Unit: milliseconds; "
                        + "null when no terminal run exists.",
                example = "8200",
                format = "int64",
                types = {"integer", "null"},
                nullable = true
        )
        Long p50MatchingDurationMs,
        @Schema(
                description = "Continuous P95 terminal matching duration. Unit: milliseconds; "
                        + "null when no terminal run exists.",
                example = "26400",
                format = "int64",
                types = {"integer", "null"},
                nullable = true
        )
        Long p95MatchingDurationMs
) {
}
