package com.example.goride.analytics.dto;

import com.example.goride.analytics.model.AnalyticsBucket;
import com.example.goride.analytics.model.AnalyticsSourceVariant;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

@Schema(
        name = "SupplyTimeseries",
        description = "Continuous driver-supply series with explicit snapshot completeness.",
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
                    "averageOnlineDrivers": 84.25,
                    "averageAvailableDrivers": 52.50,
                    "averageBusyDrivers": 31.75,
                    "snapshotCoverage": 0.9167,
                    "tripRequests": 140,
                    "requestToAvailableDriverRatio": 2.6667
                  }]
                }
                """
)
public record SupplyTimeseriesResponse(
        @Schema(description = "Inclusive normalized UTC range boundary.")
        Instant from,
        @Schema(description = "Exclusive normalized UTC range boundary.")
        Instant to,
        @Schema(description = "IANA timezone used to build calendar buckets.")
        String reportingTimezone,
        @Schema(description = "Requested bucket granularity. Supply supports HOUR and DAY.")
        AnalyticsBucket bucket,
        @Schema(description = "Physical query source used for this response.")
        AnalyticsSourceVariant sourceVariant,
        @Schema(description = "Database snapshot cutoff represented by the response.")
        Instant dataFreshnessAt,
        @Schema(description = "Ordered continuous buckets intersecting [from, to).")
        List<Point> points
) {
    @Schema(
            name = "SupplyTimeseriesPoint",
            description = "One supply bucket. Average fields are null when no snapshot exists; "
                    + "snapshotCoverage makes partial data explicit.",
            requiredProperties = {
                    "bucketStart", "averageOnlineDrivers", "averageAvailableDrivers",
                    "averageBusyDrivers", "snapshotCoverage", "tripRequests",
                    "requestToAvailableDriverRatio"
            }
    )
    public record Point(
            @Schema(
                    description = "Calendar bucket start with the reporting-timezone offset.",
                    example = "2026-07-01T08:00:00+07:00"
            )
            OffsetDateTime bucketStart,
            @Schema(
                    description = "Mean online drivers over observed snapshots. Unit: drivers; "
                            + "scale: 2; null when no snapshot exists.",
                    example = "84.25",
                    types = {"number", "null"},
                    nullable = true
            )
            BigDecimal averageOnlineDrivers,
            @Schema(
                    description = "Mean available drivers over observed snapshots. Unit: drivers; "
                            + "scale: 2; null when no snapshot exists.",
                    example = "52.50",
                    types = {"number", "null"},
                    nullable = true
            )
            BigDecimal averageAvailableDrivers,
            @Schema(
                    description = "Mean busy drivers over observed snapshots. Unit: drivers; "
                            + "scale: 2; null when no snapshot exists.",
                    example = "31.75",
                    types = {"number", "null"},
                    nullable = true
            )
            BigDecimal averageBusyDrivers,
            @Schema(
                    description = "Observed five-minute buckets / expected buckets. Unit: ratio; "
                            + "scale: 4. Values below 1 identify partial data; zero means expected "
                            + "buckets exist but none were observed; null means the selected "
                            + "interval contains no expected sample time.",
                    example = "0.9167",
                    minimum = "0",
                    maximum = "1",
                    types = {"number", "null"},
                    nullable = true
            )
            BigDecimal snapshotCoverage,
            @Schema(description = "Trip requests in the aligned demand bucket.", example = "140")
            long tripRequests,
            @Schema(
                    description = "Trip requests / average available drivers. Scale: 4; null when "
                            + "coverage is below 0.80, supply is missing, or availability is zero.",
                    example = "2.6667",
                    types = {"number", "null"},
                    nullable = true
            )
            BigDecimal requestToAvailableDriverRatio
    ) {
    }
}
