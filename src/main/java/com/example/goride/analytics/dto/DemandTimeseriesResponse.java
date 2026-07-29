package com.example.goride.analytics.dto;

import com.example.goride.analytics.model.AnalyticsBucket;
import com.example.goride.analytics.model.AnalyticsSourceVariant;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

@Schema(
        name = "DemandTimeseries",
        description = "Continuous request-cohort demand series. Missing buckets are zero-filled.",
        requiredProperties = {
                "from", "to", "reportingTimezone", "bucket", "sourceVariant",
                "dataFreshnessAt", "points"
        },
        example = """
                {
                  "from": "2026-06-30T17:00:00Z",
                  "to": "2026-07-01T17:00:00Z",
                  "reportingTimezone": "Asia/Ho_Chi_Minh",
                  "bucket": "HOUR",
                  "sourceVariant": "DIRECT",
                  "dataFreshnessAt": "2026-07-01T17:00:00Z",
                  "points": [{
                    "bucketStart": "2026-07-01T08:00:00+07:00",
                    "tripRequests": 120,
                    "completedTripsByRequestCohort": 98,
                    "completionRate": 0.8167
                  }]
                }
                """
)
public record DemandTimeseriesResponse(
        @Schema(description = "Inclusive normalized UTC range boundary.")
        Instant from,
        @Schema(description = "Exclusive normalized UTC range boundary.")
        Instant to,
        @Schema(description = "IANA timezone used to build calendar buckets.")
        String reportingTimezone,
        @Schema(description = "Requested bucket granularity.")
        AnalyticsBucket bucket,
        @Schema(description = "Physical query source used for this response.")
        AnalyticsSourceVariant sourceVariant,
        @Schema(description = "Database snapshot cutoff represented by the response.")
        Instant dataFreshnessAt,
        @Schema(description = "Ordered continuous buckets intersecting [from, to).")
        List<Point> points
) {
    @Schema(
            name = "DemandTimeseriesPoint",
            description = "One demand bucket in the reporting timezone.",
            requiredProperties = {
                    "bucketStart", "tripRequests", "completedTripsByRequestCohort",
                    "completionRate"
            }
    )
    public record Point(
            @Schema(
                    description = "Calendar bucket start with the reporting-timezone offset.",
                    example = "2026-07-01T08:00:00+07:00"
            )
            OffsetDateTime bucketStart,
            @Schema(description = "Requests in this bucket. Unit: trips.", example = "120")
            long tripRequests,
            @Schema(
                    description = "Requested trips in this bucket whose current outcome is "
                            + "COMPLETED. Unit: trips.",
                    example = "98"
            )
            long completedTripsByRequestCohort,
            @Schema(
                    description = "completedTripsByRequestCohort / tripRequests. Unit: ratio; "
                            + "scale: 4; null for a zero-count bucket.",
                    example = "0.8167",
                    minimum = "0",
                    maximum = "1",
                    types = {"number", "null"},
                    nullable = true
            )
            BigDecimal completionRate
    ) {
    }
}
