package com.example.goride.analytics.controller;

import com.example.goride.analytics.config.AdminAnalyticsOpenApiConfig;
import com.example.goride.analytics.dto.DataQualityResponse;
import com.example.goride.analytics.dto.ForecastDemandResponse;
import com.example.goride.analytics.dto.ForecastEvaluationResponse;
import com.example.goride.analytics.dto.ForecastHotspotsResponse;
import com.example.goride.analytics.dto.ForecastRunResponse;
import com.example.goride.analytics.dto.ModelVersionResponse;
import com.example.goride.analytics.dto.ProcessingRunResponse;
import com.example.goride.analytics.dto.ProcessingStatusResponse;
import com.example.goride.analytics.model.AnalyticsQueryOperation;
import com.example.goride.analytics.model.AnalyticsSourceVariant;
import com.example.goride.analytics.service.AnalyticsQueryObservation;
import com.example.goride.analytics.service.DemandForecastQueryService;
import com.example.goride.common.api.ApiResponse;
import com.example.goride.common.api.ErrorResponse;
import com.example.goride.common.api.PageResponse;
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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/v1/admin/analytics")
@PreAuthorize("hasRole('ADMIN')")
@Tag(
        name = "Admin Demand Forecasting",
        description = "Read-only processing, data-quality, model registry and spatio-temporal "
                + "demand forecast APIs. Research evaluation runs are explicitly labelled and "
                + "must not be interpreted as live operational forecasts."
)
@SecurityRequirement(name = AdminAnalyticsOpenApiConfig.BEARER_AUTH)
@ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Invalid range, timezone, horizon, grid, bounds, page or filter. "
                        + "Codes: VALIDATION_ERROR, ANALYTICS_RANGE_TOO_LARGE, "
                        + "ANALYTICS_RESULT_TOO_LARGE.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "JWT is missing, invalid or expired.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Authenticated user does not have ROLE_ADMIN.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Requested processing run does not exist. Code: ANALYTICS_RUN_NOT_FOUND.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Global API rate limit exceeded. Code: RATE_LIMIT_EXCEEDED.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "503",
                description = "Forecasting read store is unavailable. Code: ANALYTICS_DATA_UNAVAILABLE.",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
})
public class AdminDemandForecastController {
    private final DemandForecastQueryService forecastService;
    private final AnalyticsQueryObservation queryObservation;

    public AdminDemandForecastController(
            DemandForecastQueryService forecastService,
            AnalyticsQueryObservation queryObservation
    ) {
        this.forecastService = forecastService;
        this.queryObservation = queryObservation;
    }

    @GetMapping("/processing/status")
    @Operation(summary = "Get latest processing stage status and freshness")
    public ApiResponse<ProcessingStatusResponse> getProcessingStatus(
            @RequestParam(required = false) String sourceProfile
    ) {
        return observe(
                AnalyticsQueryOperation.PROCESSING_STATUS,
                null,
                null,
                DemandForecastQueryService.DEFAULT_TIMEZONE,
                () -> forecastService.getProcessingStatus(sourceProfile),
                response -> new ResultMetadata(response.generatedAt(), response.stages().size())
        );
    }

    @GetMapping("/processing/runs")
    @Operation(summary = "List processing runs with bounded pagination")
    public ApiResponse<PageResponse<ProcessingRunResponse>> getProcessingRuns(
            @RequestParam(required = false) String runType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sourceProfile,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return observe(
                AnalyticsQueryOperation.PROCESSING_RUNS,
                null,
                null,
                DemandForecastQueryService.DEFAULT_TIMEZONE,
                () -> forecastService.getProcessingRuns(
                        runType,
                        status,
                        sourceProfile,
                        page,
                        size
                ),
                response -> new ResultMetadata(
                        Instant.now(),
                        response.items().size()
                )
        );
    }

    @GetMapping("/data-quality")
    @Operation(summary = "Get quality rules and backend-computed summary for one run")
    public ApiResponse<DataQualityResponse> getDataQuality(
            @RequestParam UUID runId
    ) {
        return observe(
                AnalyticsQueryOperation.DATA_QUALITY,
                null,
                null,
                DemandForecastQueryService.DEFAULT_TIMEZONE,
                () -> forecastService.getDataQuality(runId),
                response -> new ResultMetadata(response.sourceCutoff(), response.rules().size())
        );
    }

    @GetMapping("/models")
    @Operation(
            summary = "List model registry versions",
            description = "Includes lifecycle, model card, hyperparameters, checksum and research "
                    + "scope, but never exposes artifact paths or binary model contents."
    )
    public ApiResponse<PageResponse<ModelVersionResponse>> getModels(
            @RequestParam(required = false) String lifecycleStatus,
            @RequestParam(required = false) String sourceProfile,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return observe(
                AnalyticsQueryOperation.MODEL_VERSIONS,
                null,
                null,
                DemandForecastQueryService.DEFAULT_TIMEZONE,
                () -> forecastService.getModels(lifecycleStatus, sourceProfile, page, size),
                response -> new ResultMetadata(Instant.now(), response.items().size())
        );
    }

    @GetMapping("/forecast/demand")
    @Operation(
            summary = "Get actual and predicted demand by time and spatial cell",
            description = "Returns a GeoJSON FeatureCollection. The server supplies predicted, "
                    + "actual, error, interval, lineage, units, freshness and quality metadata; "
                    + "the frontend must not recalculate these values."
    )
    public ApiResponse<ForecastDemandResponse> getDemandForecast(
            @Parameter(example = "2014-06-01T00:00:00Z")
            @RequestParam OffsetDateTime from,
            @Parameter(example = "2014-06-01T01:15:00Z")
            @RequestParam OffsetDateTime to,
            @RequestParam(defaultValue = DemandForecastQueryService.DEFAULT_TIMEZONE)
            String timezone,
            @Parameter(schema = @Schema(allowableValues = {"15", "30", "60"}))
            @RequestParam Integer horizonMinutes,
            @Parameter(schema = @Schema(allowableValues = {"250", "500", "1000", "2000"}))
            @RequestParam Integer cellSizeMeters,
            @RequestParam(required = false) String modelVersion,
            @RequestParam(required = false) UUID forecastRunId,
            @Parameter(schema = @Schema(allowableValues = {"EVALUATION", "PUBLISHED"}))
            @RequestParam(required = false) String runPurpose,
            @RequestParam(name = "minLng", required = false) BigDecimal minLongitude,
            @RequestParam(name = "minLat", required = false) BigDecimal minLatitude,
            @RequestParam(name = "maxLng", required = false) BigDecimal maxLongitude,
            @RequestParam(name = "maxLat", required = false) BigDecimal maxLatitude
    ) {
        return observe(
                AnalyticsQueryOperation.FORECAST_DEMAND,
                from,
                to,
                timezone,
                () -> forecastService.getDemandForecast(
                        from,
                        to,
                        timezone,
                        horizonMinutes,
                        cellSizeMeters,
                        modelVersion,
                        forecastRunId,
                        runPurpose,
                        minLongitude,
                        minLatitude,
                        maxLongitude,
                        maxLatitude
                ),
                response -> new ResultMetadata(
                        response.metadata().dataFreshnessAt(),
                        response.features().size()
                )
        );
    }

    @GetMapping("/forecast/hotspots")
    @Operation(summary = "Get highest predicted-demand cells")
    public ApiResponse<ForecastHotspotsResponse> getForecastHotspots(
            @RequestParam OffsetDateTime from,
            @RequestParam OffsetDateTime to,
            @RequestParam(defaultValue = DemandForecastQueryService.DEFAULT_TIMEZONE)
            String timezone,
            @RequestParam Integer horizonMinutes,
            @RequestParam Integer cellSizeMeters,
            @RequestParam(required = false) String modelVersion,
            @RequestParam(required = false) UUID forecastRunId,
            @RequestParam(required = false) String runPurpose,
            @RequestParam(name = "minLng", required = false) BigDecimal minLongitude,
            @RequestParam(name = "minLat", required = false) BigDecimal minLatitude,
            @RequestParam(name = "maxLng", required = false) BigDecimal maxLongitude,
            @RequestParam(name = "maxLat", required = false) BigDecimal maxLatitude,
            @RequestParam(defaultValue = "20") Integer limit
    ) {
        return observe(
                AnalyticsQueryOperation.FORECAST_HOTSPOTS,
                from,
                to,
                timezone,
                () -> forecastService.getForecastHotspots(
                        from,
                        to,
                        timezone,
                        horizonMinutes,
                        cellSizeMeters,
                        modelVersion,
                        forecastRunId,
                        runPurpose,
                        minLongitude,
                        minLatitude,
                        maxLongitude,
                        maxLatitude,
                        limit
                ),
                response -> new ResultMetadata(
                        response.metadata().dataFreshnessAt(),
                        response.hotspots().size()
                )
        );
    }

    @GetMapping("/forecast/evaluation")
    @Operation(
            summary = "Get backend-computed MAE, RMSE and WAPE",
            description = "Prefers persisted evaluation-store metrics. If that store is empty, "
                    + "metrics are derived in PostgreSQL from actual-backfilled forecast rows and "
                    + "the response identifies that source."
    )
    public ApiResponse<ForecastEvaluationResponse> getForecastEvaluation(
            @RequestParam(required = false) String modelVersion,
            @RequestParam(required = false) Integer horizonMinutes,
            @RequestParam(required = false) Integer cellSizeMeters
    ) {
        return observe(
                AnalyticsQueryOperation.FORECAST_EVALUATION,
                null,
                null,
                DemandForecastQueryService.DEFAULT_TIMEZONE,
                () -> forecastService.getForecastEvaluation(
                        modelVersion,
                        horizonMinutes,
                        cellSizeMeters
                ),
                response -> new ResultMetadata(response.generatedAt(), response.metrics().size())
        );
    }

    @GetMapping("/forecast/runs")
    @Operation(summary = "List forecast runs, quality, actual coverage and freshness")
    public ApiResponse<PageResponse<ForecastRunResponse>> getForecastRuns(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String runPurpose,
            @RequestParam(required = false) String modelVersion,
            @RequestParam(required = false) String sourceProfile,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return observe(
                AnalyticsQueryOperation.FORECAST_RUNS,
                null,
                null,
                DemandForecastQueryService.DEFAULT_TIMEZONE,
                () -> forecastService.getForecastRuns(
                        status,
                        runPurpose,
                        modelVersion,
                        sourceProfile,
                        page,
                        size
                ),
                response -> new ResultMetadata(Instant.now(), response.items().size())
        );
    }

    private <T> ApiResponse<T> observe(
            AnalyticsQueryOperation operation,
            OffsetDateTime from,
            OffsetDateTime to,
            String timezone,
            Supplier<T> query,
            java.util.function.Function<T, ResultMetadata> metadataExtractor
    ) {
        AnalyticsQueryObservation.Scope observation = queryObservation.start(
                operation,
                from,
                to,
                timezone
        );
        try {
            T response = query.get();
            ResultMetadata metadata = metadataExtractor.apply(response);
            observation.success(
                    AnalyticsSourceVariant.DIRECT,
                    metadata.freshnessAt(),
                    metadata.rowCount()
            );
            return ApiResponse.ok(response);
        }
        catch (RuntimeException exception) {
            observation.failure(exception);
            throw exception;
        }
    }

    private record ResultMetadata(Instant freshnessAt, long rowCount) {
    }
}
