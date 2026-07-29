package com.example.goride.analytics.controller;

import com.example.goride.analytics.dto.AnalyticsOverviewResponse;
import com.example.goride.analytics.dto.DemandHeatmapResponse;
import com.example.goride.analytics.dto.DemandTimeseriesResponse;
import com.example.goride.analytics.dto.MatchingFunnelResponse;
import com.example.goride.analytics.dto.MatchingPerformanceResponse;
import com.example.goride.analytics.dto.SupplyTimeseriesResponse;
import com.example.goride.analytics.model.AnalyticsBucket;
import com.example.goride.analytics.service.AdminAnalyticsQueryService;
import com.example.goride.common.api.ApiResponse;
import com.example.goride.driver.domain.VehicleType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/admin/analytics")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Analytics", description = "Direct-query operational and matching analytics")
public class AdminAnalyticsController {
    private final AdminAnalyticsQueryService analyticsService;

    public AdminAnalyticsController(AdminAnalyticsQueryService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/overview")
    @Operation(
            summary = "Get analytics overview",
            description = "Returns trip, completed-payment and matching KPIs for a [from, to) range."
    )
    public ApiResponse<AnalyticsOverviewResponse> getOverview(
            @Parameter(example = "2026-07-01T00:00:00+07:00")
            @RequestParam OffsetDateTime from,
            @Parameter(example = "2026-08-01T00:00:00+07:00")
            @RequestParam OffsetDateTime to,
            @Parameter(example = "Asia/Ho_Chi_Minh")
            @RequestParam(defaultValue = AdminAnalyticsQueryService.DEFAULT_TIMEZONE) String timezone,
            @RequestParam(required = false) VehicleType vehicleType,
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
            description = "Returns continuous HOUR, DAY or WEEK request-cohort buckets."
    )
    public ApiResponse<DemandTimeseriesResponse> getDemandTimeseries(
            @Parameter(example = "2026-07-01T00:00:00+07:00")
            @RequestParam OffsetDateTime from,
            @Parameter(example = "2026-07-08T00:00:00+07:00")
            @RequestParam OffsetDateTime to,
            @Parameter(example = "Asia/Ho_Chi_Minh")
            @RequestParam(defaultValue = AdminAnalyticsQueryService.DEFAULT_TIMEZONE) String timezone,
            @RequestParam(required = false) VehicleType vehicleType,
            @RequestParam(required = false) Long serviceAreaId,
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
            description = "Returns a bounded EPSG:4326 GeoJSON square-grid aggregation of pickup demand."
    )
    public ApiResponse<DemandHeatmapResponse> getDemandHeatmap(
            @Parameter(example = "2026-07-01T00:00:00+07:00")
            @RequestParam OffsetDateTime from,
            @Parameter(example = "2026-07-08T00:00:00+07:00")
            @RequestParam OffsetDateTime to,
            @Parameter(example = "Asia/Ho_Chi_Minh")
            @RequestParam(defaultValue = AdminAnalyticsQueryService.DEFAULT_TIMEZONE) String timezone,
            @RequestParam(required = false) VehicleType vehicleType,
            @RequestParam(required = false) Long serviceAreaId,
            @Parameter(example = "1000")
            @RequestParam Integer cellSizeMeters,
            @Parameter(example = "106.60")
            @RequestParam(name = "minLng", required = false) BigDecimal minLongitude,
            @Parameter(example = "10.70")
            @RequestParam(name = "minLat", required = false) BigDecimal minLatitude,
            @Parameter(example = "106.80")
            @RequestParam(name = "maxLng", required = false) BigDecimal maxLongitude,
            @Parameter(example = "10.90")
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
            description = "Returns continuous HOUR or DAY supply buckets and snapshot coverage."
    )
    public ApiResponse<SupplyTimeseriesResponse> getSupplyTimeseries(
            @Parameter(example = "2026-07-01T00:00:00+07:00")
            @RequestParam OffsetDateTime from,
            @Parameter(example = "2026-07-02T00:00:00+07:00")
            @RequestParam OffsetDateTime to,
            @Parameter(example = "Asia/Ho_Chi_Minh")
            @RequestParam(defaultValue = AdminAnalyticsQueryService.DEFAULT_TIMEZONE) String timezone,
            @RequestParam(required = false) VehicleType vehicleType,
            @RequestParam(required = false) Long serviceAreaId,
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
            description = "Returns terminal run, duration, offer outcome and candidate metrics."
    )
    public ApiResponse<MatchingPerformanceResponse> getMatchingPerformance(
            @Parameter(example = "2026-07-01T00:00:00+07:00")
            @RequestParam OffsetDateTime from,
            @Parameter(example = "2026-07-08T00:00:00+07:00")
            @RequestParam OffsetDateTime to,
            @Parameter(example = "Asia/Ho_Chi_Minh")
            @RequestParam(defaultValue = AdminAnalyticsQueryService.DEFAULT_TIMEZONE) String timezone,
            @RequestParam(required = false) VehicleType vehicleType,
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
            description = "Returns run-based funnel steps and the final distinct-trip completion step."
    )
    public ApiResponse<MatchingFunnelResponse> getMatchingFunnel(
            @Parameter(example = "2026-07-01T00:00:00+07:00")
            @RequestParam OffsetDateTime from,
            @Parameter(example = "2026-07-08T00:00:00+07:00")
            @RequestParam OffsetDateTime to,
            @Parameter(example = "Asia/Ho_Chi_Minh")
            @RequestParam(defaultValue = AdminAnalyticsQueryService.DEFAULT_TIMEZONE) String timezone,
            @RequestParam(required = false) VehicleType vehicleType,
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
