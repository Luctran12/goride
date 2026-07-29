package com.example.goride.analytics.controller;

import com.example.goride.analytics.config.AdminAnalyticsOpenApiConfig;
import com.example.goride.analytics.dto.AdminAnalyticsApiResponses;
import com.example.goride.analytics.dto.AnalyticsOverviewResponse;
import com.example.goride.analytics.dto.DemandHeatmapResponse;
import com.example.goride.analytics.dto.DemandTimeseriesResponse;
import com.example.goride.analytics.dto.MatchingFunnelResponse;
import com.example.goride.analytics.dto.MatchingPerformanceResponse;
import com.example.goride.analytics.dto.SupplyTimeseriesResponse;
import com.example.goride.analytics.model.AnalyticsBucket;
import com.example.goride.analytics.service.AdminAnalyticsQueryService;
import com.example.goride.common.api.ApiResponse;
import com.example.goride.common.api.ErrorResponse;
import com.example.goride.driver.domain.VehicleType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/v1/admin/analytics")
@PreAuthorize("hasRole('ADMIN')")
@Tag(
        name = "Admin Analytics",
        description = "Read-only trip, revenue, supply, spatial-demand and matching analytics. "
                + "All ranges use [from, to) semantics and every response identifies its source "
                + "variant and data snapshot cutoff."
)
@SecurityRequirement(name = AdminAnalyticsOpenApiConfig.BEARER_AUTH)
@ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Invalid parameter, unsupported enum, excessive range/bounds, "
                        + "or excessive result size. Codes: VALIDATION_ERROR, "
                        + "ANALYTICS_RANGE_TOO_LARGE, ANALYTICS_RESULT_TOO_LARGE.",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ErrorResponse.class)
                )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "JWT is missing, invalid or expired.",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ErrorResponse.class)
                )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Authenticated user does not have ROLE_ADMIN.",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ErrorResponse.class)
                )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "The selected serviceAreaId does not exist. "
                        + "Code: SERVICE_AREA_NOT_FOUND.",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ErrorResponse.class)
                )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "503",
                description = "The configured analytics source is unavailable and direct fallback "
                        + "is disabled. Code: ANALYTICS_DATA_UNAVAILABLE.",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ErrorResponse.class)
                )
        )
})
public class AdminAnalyticsController {
    private final AdminAnalyticsQueryService analyticsService;

    public AdminAnalyticsController(AdminAnalyticsQueryService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/overview")
    @Operation(
            summary = "Get analytics overview",
            description = "Returns backend-computed trip, completed-payment and matching KPIs. "
                    + "Counts use their documented event/cohort timestamps; ratios have scale 4; "
                    + "durations are whole milliseconds. A valid empty range returns zero counts "
                    + "and revenue with nullable ratios, averages and percentiles."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Overview returned, including a valid empty result.",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(
                            implementation = AdminAnalyticsApiResponses.Overview.class
                    )
            )
    )
    public ApiResponse<AnalyticsOverviewResponse> getOverview(
            @Parameter(
                    description = "Inclusive ISO-8601 timestamp with an explicit offset or Z.",
                    example = "2026-07-01T00:00:00+07:00"
            )
            @RequestParam OffsetDateTime from,
            @Parameter(
                    description = "Exclusive ISO-8601 timestamp with an explicit offset or Z. "
                            + "The general maximum range is 366 days.",
                    example = "2026-08-01T00:00:00+07:00"
            )
            @RequestParam OffsetDateTime to,
            @Parameter(
                    description = "IANA timezone used for calendar semantics. Defaults to "
                            + "Asia/Ho_Chi_Minh.",
                    example = "Asia/Ho_Chi_Minh"
            )
            @RequestParam(defaultValue = AdminAnalyticsQueryService.DEFAULT_TIMEZONE) String timezone,
            @Parameter(
                    description = "Optional vehicle dimension. Omit to include all vehicle types.",
                    schema = @Schema(
                            allowableValues = {"MOTORBIKE", "CAR_4_SEAT", "CAR_7_SEAT"}
                    )
            )
            @RequestParam(required = false) VehicleType vehicleType,
            @Parameter(
                    description = "Optional positive service-area ID. Omit for the global result.",
                    example = "12",
                    schema = @Schema(type = "integer", format = "int64", minimum = "1")
            )
            @RequestParam(required = false) Long serviceAreaId
    ) {
        return ApiResponse.ok(analyticsService.getOverview(
                from,
                to,
                timezone,
                vehicleType,
                serviceAreaId
        ));
    }

    @GetMapping("/demand/timeseries")
    @Operation(
            summary = "Get demand timeseries",
            description = "Returns continuous HOUR, DAY or WEEK request-cohort buckets in the "
                    + "reporting timezone. Missing buckets are returned with zero counts; "
                    + "completionRate is null when a bucket has no requests."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Continuous demand series returned.",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(
                            implementation = AdminAnalyticsApiResponses.DemandTimeseries.class
                    )
            )
    )
    public ApiResponse<DemandTimeseriesResponse> getDemandTimeseries(
            @Parameter(
                    description = "Inclusive ISO-8601 timestamp with an explicit offset or Z.",
                    example = "2026-07-01T00:00:00+07:00"
            )
            @RequestParam OffsetDateTime from,
            @Parameter(
                    description = "Exclusive ISO-8601 timestamp with an explicit offset or Z. "
                            + "The maximum range is 366 days.",
                    example = "2026-07-08T00:00:00+07:00"
            )
            @RequestParam OffsetDateTime to,
            @Parameter(
                    description = "IANA timezone used to create calendar buckets.",
                    example = "Asia/Ho_Chi_Minh"
            )
            @RequestParam(defaultValue = AdminAnalyticsQueryService.DEFAULT_TIMEZONE) String timezone,
            @Parameter(
                    description = "Optional vehicle dimension. Omit to include all vehicle types.",
                    schema = @Schema(
                            allowableValues = {"MOTORBIKE", "CAR_4_SEAT", "CAR_7_SEAT"}
                    )
            )
            @RequestParam(required = false) VehicleType vehicleType,
            @Parameter(
                    description = "Optional positive service-area ID. Omit for the global series.",
                    example = "12",
                    schema = @Schema(type = "integer", format = "int64", minimum = "1")
            )
            @RequestParam(required = false) Long serviceAreaId,
            @Parameter(
                    description = "Demand calendar bucket.",
                    example = "HOUR",
                    schema = @Schema(allowableValues = {"HOUR", "DAY", "WEEK"})
            )
            @RequestParam AnalyticsBucket bucket
    ) {
        return ApiResponse.ok(analyticsService.getDemandTimeseries(
                from,
                to,
                timezone,
                vehicleType,
                serviceAreaId,
                bucket
        ));
    }

    @GetMapping("/demand/heatmap")
    @Operation(
            summary = "Get spatial demand heatmap",
            description = "Returns at most 5,000 square-grid pickup-demand cells as an EPSG:4326 "
                    + "GeoJSON FeatureCollection. The maximum range is 31 days. Bounding "
                    + "coordinates are optional but all four must be supplied together and their "
                    + "configured area limit is 25,000 km² by default."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "GeoJSON FeatureCollection returned; features may be empty.",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(
                            implementation = AdminAnalyticsApiResponses.DemandHeatmap.class
                    )
            )
    )
    public ApiResponse<DemandHeatmapResponse> getDemandHeatmap(
            @Parameter(
                    description = "Inclusive ISO-8601 timestamp with an explicit offset or Z.",
                    example = "2026-07-01T00:00:00+07:00"
            )
            @RequestParam OffsetDateTime from,
            @Parameter(
                    description = "Exclusive ISO-8601 timestamp with an explicit offset or Z. "
                            + "The heatmap maximum range is 31 days.",
                    example = "2026-07-08T00:00:00+07:00"
            )
            @RequestParam OffsetDateTime to,
            @Parameter(
                    description = "IANA timezone used for the time filter.",
                    example = "Asia/Ho_Chi_Minh"
            )
            @RequestParam(defaultValue = AdminAnalyticsQueryService.DEFAULT_TIMEZONE) String timezone,
            @Parameter(
                    description = "Optional vehicle dimension. Omit to include all vehicle types.",
                    schema = @Schema(
                            allowableValues = {"MOTORBIKE", "CAR_4_SEAT", "CAR_7_SEAT"}
                    )
            )
            @RequestParam(required = false) VehicleType vehicleType,
            @Parameter(
                    description = "Optional positive service-area ID. Omit for global demand.",
                    example = "12",
                    schema = @Schema(type = "integer", format = "int64", minimum = "1")
            )
            @RequestParam(required = false) Long serviceAreaId,
            @Parameter(
                    description = "Square-grid edge length in meters. Supported values: "
                            + "250, 500, 1000 and 2000.",
                    example = "1000",
                    schema = @Schema(
                            type = "integer",
                            format = "int32",
                            minimum = "1"
                    )
            )
            @RequestParam Integer cellSizeMeters,
            @Parameter(
                    description = "Optional WGS84 minimum longitude; requires all other bounds.",
                    example = "106.60",
                    schema = @Schema(type = "number", minimum = "-180", maximum = "180")
            )
            @RequestParam(name = "minLng", required = false) BigDecimal minLongitude,
            @Parameter(
                    description = "Optional WGS84 minimum latitude; requires all other bounds.",
                    example = "10.70",
                    schema = @Schema(type = "number", minimum = "-90", maximum = "90")
            )
            @RequestParam(name = "minLat", required = false) BigDecimal minLatitude,
            @Parameter(
                    description = "Optional WGS84 maximum longitude; requires all other bounds.",
                    example = "106.80",
                    schema = @Schema(type = "number", minimum = "-180", maximum = "180")
            )
            @RequestParam(name = "maxLng", required = false) BigDecimal maxLongitude,
            @Parameter(
                    description = "Optional WGS84 maximum latitude; requires all other bounds.",
                    example = "10.90",
                    schema = @Schema(type = "number", minimum = "-90", maximum = "90")
            )
            @RequestParam(name = "maxLat", required = false) BigDecimal maxLatitude
    ) {
        return ApiResponse.ok(analyticsService.getDemandHeatmap(
                from,
                to,
                timezone,
                vehicleType,
                serviceAreaId,
                cellSizeMeters,
                minLongitude,
                minLatitude,
                maxLongitude,
                maxLatitude
        ));
    }

    @GetMapping("/supply/timeseries")
    @Operation(
            summary = "Get driver-supply timeseries",
            description = "Returns continuous HOUR or DAY driver-supply buckets. Snapshot coverage "
                    + "distinguishes complete, partial and missing supply data. "
                    + "requestToAvailableDriverRatio is suppressed below 0.80 coverage."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Continuous supply series returned with completeness metadata.",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(
                            implementation = AdminAnalyticsApiResponses.SupplyTimeseries.class
                    )
            )
    )
    public ApiResponse<SupplyTimeseriesResponse> getSupplyTimeseries(
            @Parameter(
                    description = "Inclusive ISO-8601 timestamp with an explicit offset or Z.",
                    example = "2026-07-01T00:00:00+07:00"
            )
            @RequestParam OffsetDateTime from,
            @Parameter(
                    description = "Exclusive ISO-8601 timestamp with an explicit offset or Z. "
                            + "The maximum range is 366 days.",
                    example = "2026-07-02T00:00:00+07:00"
            )
            @RequestParam OffsetDateTime to,
            @Parameter(
                    description = "IANA timezone used to create calendar buckets.",
                    example = "Asia/Ho_Chi_Minh"
            )
            @RequestParam(defaultValue = AdminAnalyticsQueryService.DEFAULT_TIMEZONE) String timezone,
            @Parameter(
                    description = "Optional vehicle dimension. Omit to include all vehicle types.",
                    schema = @Schema(
                            allowableValues = {"MOTORBIKE", "CAR_4_SEAT", "CAR_7_SEAT"}
                    )
            )
            @RequestParam(required = false) VehicleType vehicleType,
            @Parameter(
                    description = "Optional positive service-area ID. Omit for the global series.",
                    example = "12",
                    schema = @Schema(type = "integer", format = "int64", minimum = "1")
            )
            @RequestParam(required = false) Long serviceAreaId,
            @Parameter(
                    description = "Supply calendar bucket.",
                    example = "HOUR",
                    schema = @Schema(allowableValues = {"HOUR", "DAY"})
            )
            @RequestParam AnalyticsBucket bucket
    ) {
        return ApiResponse.ok(analyticsService.getSupplyTimeseries(
                from,
                to,
                timezone,
                vehicleType,
                serviceAreaId,
                bucket
        ));
    }

    @GetMapping("/matching/performance")
    @Operation(
            summary = "Get matching performance",
            description = "Returns backend-computed terminal-run rates, exact P50/P95 durations, "
                    + "offer outcomes and candidate metrics. Open runs and non-terminal offers are "
                    + "excluded from terminal rates; empty denominators return null."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Backend-computed matching metrics returned.",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(
                            implementation = AdminAnalyticsApiResponses.MatchingPerformance.class
                    )
            )
    )
    public ApiResponse<MatchingPerformanceResponse> getMatchingPerformance(
            @Parameter(
                    description = "Inclusive ISO-8601 timestamp with an explicit offset or Z.",
                    example = "2026-07-01T00:00:00+07:00"
            )
            @RequestParam OffsetDateTime from,
            @Parameter(
                    description = "Exclusive ISO-8601 timestamp with an explicit offset or Z. "
                            + "The maximum range is 366 days.",
                    example = "2026-07-08T00:00:00+07:00"
            )
            @RequestParam OffsetDateTime to,
            @Parameter(
                    description = "IANA timezone used for calendar semantics.",
                    example = "Asia/Ho_Chi_Minh"
            )
            @RequestParam(defaultValue = AdminAnalyticsQueryService.DEFAULT_TIMEZONE) String timezone,
            @Parameter(
                    description = "Optional vehicle dimension. Omit to include all vehicle types.",
                    schema = @Schema(
                            allowableValues = {"MOTORBIKE", "CAR_4_SEAT", "CAR_7_SEAT"}
                    )
            )
            @RequestParam(required = false) VehicleType vehicleType,
            @Parameter(
                    description = "Optional positive service-area ID. Omit for global metrics.",
                    example = "12",
                    schema = @Schema(type = "integer", format = "int64", minimum = "1")
            )
            @RequestParam(required = false) Long serviceAreaId
    ) {
        return ApiResponse.ok(analyticsService.getMatchingPerformance(
                from,
                to,
                timezone,
                vehicleType,
                serviceAreaId
        ));
    }

    @GetMapping("/matching/funnel")
    @Operation(
            summary = "Get matching funnel",
            description = "Returns five stable ordered steps. RUN_STARTED, CANDIDATE_FOUND, "
                    + "OFFER_SENT and OFFER_ACCEPTED count distinct matching runs; TRIP_COMPLETED "
                    + "counts distinct trips. OFFER_SENT is not the total number of offers."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Five stable ordered funnel steps returned.",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(
                            implementation = AdminAnalyticsApiResponses.MatchingFunnel.class
                    )
            )
    )
    public ApiResponse<MatchingFunnelResponse> getMatchingFunnel(
            @Parameter(
                    description = "Inclusive ISO-8601 timestamp with an explicit offset or Z.",
                    example = "2026-07-01T00:00:00+07:00"
            )
            @RequestParam OffsetDateTime from,
            @Parameter(
                    description = "Exclusive ISO-8601 timestamp with an explicit offset or Z. "
                            + "The maximum range is 366 days.",
                    example = "2026-07-08T00:00:00+07:00"
            )
            @RequestParam OffsetDateTime to,
            @Parameter(
                    description = "IANA timezone used for calendar semantics.",
                    example = "Asia/Ho_Chi_Minh"
            )
            @RequestParam(defaultValue = AdminAnalyticsQueryService.DEFAULT_TIMEZONE) String timezone,
            @Parameter(
                    description = "Optional vehicle dimension. Omit to include all vehicle types.",
                    schema = @Schema(
                            allowableValues = {"MOTORBIKE", "CAR_4_SEAT", "CAR_7_SEAT"}
                    )
            )
            @RequestParam(required = false) VehicleType vehicleType,
            @Parameter(
                    description = "Optional positive service-area ID. Omit for the global funnel.",
                    example = "12",
                    schema = @Schema(type = "integer", format = "int64", minimum = "1")
            )
            @RequestParam(required = false) Long serviceAreaId
    ) {
        return ApiResponse.ok(analyticsService.getMatchingFunnel(
                from,
                to,
                timezone,
                vehicleType,
                serviceAreaId
        ));
    }
}
