package com.example.goride.analytics.service;

import com.example.goride.analytics.config.AnalyticsSpatialProperties;
import com.example.goride.analytics.config.DemandForecastServingProperties;
import com.example.goride.analytics.dto.DataQualityResponse;
import com.example.goride.analytics.dto.ForecastDemandResponse;
import com.example.goride.analytics.dto.ForecastEvaluationResponse;
import com.example.goride.analytics.dto.ForecastHotspotsResponse;
import com.example.goride.analytics.dto.ForecastResponseMetadata;
import com.example.goride.analytics.dto.ForecastRunResponse;
import com.example.goride.analytics.dto.ModelVersionResponse;
import com.example.goride.analytics.dto.ProcessingRunResponse;
import com.example.goride.analytics.dto.ProcessingStatusResponse;
import com.example.goride.analytics.model.SpatialBounds;
import com.example.goride.analytics.repository.DemandForecastQueryPort;
import com.example.goride.common.api.PageResponse;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@Transactional(readOnly = true, timeout = 10, isolation = Isolation.READ_COMMITTED)
public class DemandForecastQueryService {
    public static final String DEFAULT_TIMEZONE = "UTC";

    private static final String DEMAND_UNIT = "trip_requests_per_15_minute_bucket";
    private static final Set<String> PROCESSING_RUN_TYPES = Set.of(
            "EXTRACTION", "QUALITY", "FEATURE_BUILD", "TRAINING", "EVALUATION",
            "FORECAST", "ACTUAL_BACKFILL"
    );
    private static final Set<String> PROCESSING_STATUSES = Set.of(
            "PENDING", "RUNNING", "SUCCEEDED", "FAILED", "CANCELLED"
    );
    private static final Set<String> MODEL_STATUSES = Set.of(
            "DRAFT", "VALIDATED", "APPROVED", "REJECTED", "RETIRED"
    );
    private static final Set<String> FORECAST_STATUSES = Set.of(
            "PENDING", "RUNNING", "SUCCEEDED", "PUBLISHED", "FAILED", "CANCELLED"
    );
    private static final Set<String> FORECAST_PURPOSES = Set.of("EVALUATION", "PUBLISHED");
    private static final double EARTH_RADIUS_KM = 6371.0088;
    private static final Logger log = LoggerFactory.getLogger(DemandForecastQueryService.class);

    private final DemandForecastQueryPort queryPort;
    private final DemandForecastServingProperties properties;
    private final AnalyticsSpatialProperties spatialProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public DemandForecastQueryService(
            DemandForecastQueryPort queryPort,
            DemandForecastServingProperties properties,
            AnalyticsSpatialProperties spatialProperties,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.queryPort = queryPort;
        this.properties = properties;
        this.spatialProperties = spatialProperties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public ProcessingStatusResponse getProcessingStatus(String sourceProfile) {
        String normalizedProfile = optionalText(sourceProfile, "sourceProfile", 80);
        return database("processingStatus", () -> {
            Instant now = clock.instant();
            List<ProcessingStatusResponse.Stage> stages = queryPort
                    .latestProcessingStages(normalizedProfile)
                    .stream()
                    .map(row -> processingStage(row, now))
                    .toList();
            return new ProcessingStatusResponse(
                    now,
                    properties.getProcessingStaleAfter().toSeconds(),
                    stages
            );
        });
    }

    public PageResponse<ProcessingRunResponse> getProcessingRuns(
            String runType,
            String status,
            String sourceProfile,
            int page,
            int size
    ) {
        String normalizedType = optionalEnum(runType, "runType", PROCESSING_RUN_TYPES);
        String normalizedStatus = optionalEnum(status, "status", PROCESSING_STATUSES);
        String normalizedProfile = optionalText(sourceProfile, "sourceProfile", 80);
        int offset = normalizePage(page, size);
        return database("processingRuns", () -> PageResponse.of(
                queryPort.processingRuns(
                                normalizedType,
                                normalizedStatus,
                                normalizedProfile,
                                offset,
                                size
                        )
                        .stream()
                        .map(this::processingRun)
                        .toList(),
                page,
                size,
                queryPort.countProcessingRuns(
                        normalizedType,
                        normalizedStatus,
                        normalizedProfile
                )
        ));
    }

    public DataQualityResponse getDataQuality(UUID runId) {
        if (runId == null) {
            throw validation("runId", "runId is required");
        }
        return database("dataQuality", () -> {
            DemandForecastQueryPort.ProcessingRunSummaryRow run = queryPort.processingRun(runId)
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.ANALYTICS_RUN_NOT_FOUND,
                            ErrorCode.ANALYTICS_RUN_NOT_FOUND.defaultMessage(),
                            Map.of("runId", runId)
                    ));
            List<DataQualityResponse.Rule> rules = queryPort.qualityRules(runId)
                    .stream()
                    .map(this::qualityRule)
                    .toList();
            long pass = rules.stream().filter(rule -> "PASS".equals(rule.resultStatus())).count();
            long warn = rules.stream().filter(rule -> "WARN".equals(rule.resultStatus())).count();
            long fail = rules.stream().filter(rule -> "FAIL".equals(rule.resultStatus())).count();
            return new DataQualityResponse(
                    run.runId(),
                    run.runType(),
                    run.status(),
                    run.sourceProfile(),
                    run.datasetVersion(),
                    run.sourceCutoff(),
                    new ProcessingStatusResponse.QualitySummary(pass, warn, fail),
                    rules
            );
        });
    }

    public PageResponse<ModelVersionResponse> getModels(
            String lifecycleStatus,
            String sourceProfile,
            int page,
            int size
    ) {
        String normalizedStatus = optionalEnum(
                lifecycleStatus,
                "lifecycleStatus",
                MODEL_STATUSES
        );
        String normalizedProfile = optionalText(sourceProfile, "sourceProfile", 80);
        int offset = normalizePage(page, size);
        return database("models", () -> PageResponse.of(
                queryPort.modelVersions(normalizedStatus, normalizedProfile, offset, size)
                        .stream()
                        .map(this::modelVersion)
                        .toList(),
                page,
                size,
                queryPort.countModelVersions(normalizedStatus, normalizedProfile)
        ));
    }

    public ForecastDemandResponse getDemandForecast(
            OffsetDateTime from,
            OffsetDateTime to,
            String timezone,
            Integer horizonMinutes,
            Integer cellSizeMeters,
            String modelVersion,
            UUID forecastRunId,
            String runPurpose,
            BigDecimal minLongitude,
            BigDecimal minLatitude,
            BigDecimal maxLongitude,
            BigDecimal maxLatitude
    ) {
        ForecastFilter filter = normalizeForecastFilter(
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
        );
        return database("forecastDemand", () -> {
            var selected = queryPort.selectForecastRun(
                    filter.forecastRunId(),
                    filter.modelVersion(),
                    filter.runPurpose(),
                    filter.cellSizeMeters()
            );
            if (selected.isEmpty()) {
                return new ForecastDemandResponse(
                        "FeatureCollection",
                        unavailableMetadata(filter),
                        List.of()
                );
            }
            DemandForecastQueryPort.SelectedForecastRunRow run = selected.orElseThrow();
            List<DemandForecastQueryPort.ForecastPointRow> rows = queryPort.demandForecasts(
                    run.forecastRunId(),
                    filter.from(),
                    filter.to(),
                    filter.horizonMinutes(),
                    filter.bounds(),
                    properties.getMaximumForecastRows() + 1
            );
            if (rows.size() > properties.getMaximumForecastRows()) {
                throw new BusinessException(
                        ErrorCode.ANALYTICS_RESULT_TOO_LARGE,
                        "Forecast result exceeds the configured response cap",
                        Map.of("maximumRows", properties.getMaximumForecastRows())
                );
            }
            List<ForecastDemandResponse.Feature> features = rows.stream()
                    .map(this::forecastFeature)
                    .toList();
            return new ForecastDemandResponse(
                    "FeatureCollection",
                    forecastMetadata(run, filter, !features.isEmpty(), suppressedActualRows(rows)),
                    features
            );
        });
    }

    public ForecastHotspotsResponse getForecastHotspots(
            OffsetDateTime from,
            OffsetDateTime to,
            String timezone,
            Integer horizonMinutes,
            Integer cellSizeMeters,
            String modelVersion,
            UUID forecastRunId,
            String runPurpose,
            BigDecimal minLongitude,
            BigDecimal minLatitude,
            BigDecimal maxLongitude,
            BigDecimal maxLatitude,
            Integer limit
    ) {
        ForecastFilter filter = normalizeForecastFilter(
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
        );
        int normalizedLimit = normalizeHotspotLimit(limit);
        return database("forecastHotspots", () -> {
            var selected = queryPort.selectForecastRun(
                    filter.forecastRunId(),
                    filter.modelVersion(),
                    filter.runPurpose(),
                    filter.cellSizeMeters()
            );
            if (selected.isEmpty()) {
                return new ForecastHotspotsResponse(unavailableMetadata(filter), List.of());
            }
            DemandForecastQueryPort.SelectedForecastRunRow run = selected.orElseThrow();
            List<DemandForecastQueryPort.ForecastPointRow> rows = queryPort.forecastHotspots(
                    run.forecastRunId(),
                    filter.from(),
                    filter.to(),
                    filter.horizonMinutes(),
                    filter.bounds(),
                    normalizedLimit
            );
            List<ForecastHotspotsResponse.Hotspot> hotspots = new java.util.ArrayList<>();
            for (int index = 0; index < rows.size(); index++) {
                var row = rows.get(index);
                hotspots.add(new ForecastHotspotsResponse.Hotspot(
                        index + 1,
                        row.cellId(),
                        geometry(row.geometryJson()),
                        row.targetBucketStart(),
                        row.predictedDemand(),
                        row.predictionLower(),
                        row.predictionUpper(),
                        publishedActualDemand(row),
                        publishedAbsoluteError(row),
                        evaluationStatus(row)
                ));
            }
            return new ForecastHotspotsResponse(
                    forecastMetadata(run, filter, !hotspots.isEmpty(), suppressedActualRows(rows)),
                    List.copyOf(hotspots)
            );
        });
    }

    public ForecastEvaluationResponse getForecastEvaluation(
            String modelVersion,
            Integer horizonMinutes,
            Integer cellSizeMeters
    ) {
        String normalizedModel = optionalText(modelVersion, "modelVersion", 80);
        Integer normalizedHorizon = optionalHorizon(horizonMinutes);
        Integer normalizedCellSize = optionalCellSize(cellSizeMeters);
        return database("forecastEvaluation", () -> {
            int queryLimit = properties.getMaximumEvaluationRows() + 1;
            List<DemandForecastQueryPort.EvaluationMetricRow> metrics =
                    queryPort.storedEvaluationMetrics(
                            normalizedModel,
                            normalizedHorizon,
                            normalizedCellSize,
                            queryLimit
                    );
            String source = "PERSISTED_EVALUATION_STORE";
            if (metrics.isEmpty()) {
                metrics = queryPort.derivedEvaluationMetrics(
                        normalizedModel,
                        normalizedHorizon,
                        normalizedCellSize,
                        queryLimit
                );
                source = "DERIVED_FROM_BACKFILLED_ACTUALS";
            }
            if (metrics.size() > properties.getMaximumEvaluationRows()) {
                throw new BusinessException(
                        ErrorCode.ANALYTICS_RESULT_TOO_LARGE,
                        "Evaluation result exceeds the configured response cap",
                        Map.of("maximumRows", properties.getMaximumEvaluationRows())
                );
            }
            List<ForecastEvaluationResponse.Metric> responseMetrics = metrics.stream()
                    .map(this::evaluationMetric)
                    .toList();
            return new ForecastEvaluationResponse(
                    clock.instant(),
                    source,
                    DEMAND_UNIT,
                    responseMetrics
            );
        });
    }

    public PageResponse<ForecastRunResponse> getForecastRuns(
            String status,
            String runPurpose,
            String modelVersion,
            String sourceProfile,
            int page,
            int size
    ) {
        String normalizedStatus = optionalEnum(status, "status", FORECAST_STATUSES);
        String normalizedPurpose = optionalEnum(runPurpose, "runPurpose", FORECAST_PURPOSES);
        String normalizedModel = optionalText(modelVersion, "modelVersion", 80);
        String normalizedProfile = optionalText(sourceProfile, "sourceProfile", 80);
        int offset = normalizePage(page, size);
        return database("forecastRuns", () -> PageResponse.of(
                queryPort.forecastRuns(
                                normalizedStatus,
                                normalizedPurpose,
                                normalizedModel,
                                normalizedProfile,
                                offset,
                                size
                        )
                        .stream()
                        .map(this::forecastRun)
                        .toList(),
                page,
                size,
                queryPort.countForecastRuns(
                        normalizedStatus,
                        normalizedPurpose,
                        normalizedModel,
                        normalizedProfile
                )
        ));
    }

    private ProcessingStatusResponse.Stage processingStage(
            DemandForecastQueryPort.ProcessingStageRow row,
            Instant now
    ) {
        Instant reference = row.finishedAt() == null ? row.startedAt() : row.finishedAt();
        Long age = nullableAgeSeconds(reference, now);
        String freshness;
        if ("RUNNING".equals(row.status()) || "PENDING".equals(row.status())) {
            freshness = row.status();
        }
        else if ("FAILED".equals(row.status()) || "CANCELLED".equals(row.status())) {
            freshness = row.status();
        }
        else if (age != null && age > properties.getProcessingStaleAfter().toSeconds()) {
            freshness = "STALE";
        }
        else {
            freshness = "FRESH";
        }
        return new ProcessingStatusResponse.Stage(
                row.runId(),
                row.artifactRunId(),
                row.runType(),
                row.status(),
                row.sourceProfile(),
                row.datasetVersion(),
                row.sourceCutoff(),
                row.startedAt(),
                row.finishedAt(),
                age,
                freshness,
                row.rowsRead(),
                row.rowsWritten(),
                quality(row.quality())
        );
    }

    private ProcessingRunResponse processingRun(DemandForecastQueryPort.ProcessingRunRow row) {
        return new ProcessingRunResponse(
                row.runId(),
                row.artifactRunId(),
                row.runType(),
                row.status(),
                row.sourceProfile(),
                row.datasetVersion(),
                row.sourceCutoff(),
                row.codeCommit(),
                row.attemptNo(),
                row.rowsRead(),
                row.rowsWritten(),
                row.createdAt(),
                row.startedAt(),
                row.finishedAt(),
                row.errorCode(),
                row.errorMessage(),
                quality(row.quality())
        );
    }

    private DataQualityResponse.Rule qualityRule(DemandForecastQueryPort.QualityRuleRow row) {
        return new DataQualityResponse.Rule(
                row.ruleCode(),
                row.scopeKey(),
                row.severity(),
                row.resultStatus(),
                row.recordsChecked(),
                row.recordsBreached(),
                row.metricValue(),
                row.threshold(),
                row.details(),
                row.evaluatedAt()
        );
    }

    private ModelVersionResponse modelVersion(DemandForecastQueryPort.ModelVersionRow row) {
        return new ModelVersionResponse(
                row.modelVersionId(),
                row.modelName(),
                row.modelVersion(),
                row.modelFamily(),
                row.lifecycleStatus(),
                row.approvalScope(),
                row.sourceProfile(),
                row.datasetVersion(),
                row.demandEventSemantics(),
                row.featureSetVersion(),
                row.gridVersion(),
                row.cellSizeMeters(),
                row.bucketMinutes(),
                row.trainingCutoff(),
                row.artifactSha256(),
                row.hyperparameters(),
                row.modelCard(),
                row.createdAt(),
                row.validatedAt(),
                row.approvedAt(),
                row.retiredAt()
        );
    }

    private ForecastDemandResponse.Feature forecastFeature(
            DemandForecastQueryPort.ForecastPointRow row
    ) {
        return new ForecastDemandResponse.Feature(
                "Feature",
                geometry(row.geometryJson()),
                new ForecastDemandResponse.Properties(
                        row.cellId(),
                        row.targetBucketStart(),
                        row.horizonMinutes(),
                        row.predictedDemand(),
                        row.predictionLower(),
                        row.predictionUpper(),
                        publishedActualDemand(row),
                        publishedAbsoluteError(row),
                        actualSuppressed(row) ? null : row.evaluatedAt(),
                        evaluationStatus(row)
                )
        );
    }

    private ForecastDemandResponse.Polygon geometry(String geometryJson) {
        try {
            JsonNode root = objectMapper.readTree(geometryJson);
            if (root == null || !"Polygon".equals(root.path("type").asText())
                    || !root.path("coordinates").isArray()) {
                throw new JsonProcessingException("Forecast geometry is not a GeoJSON Polygon") {
                };
            }
            List<List<List<BigDecimal>>> coordinates = objectMapper.convertValue(
                    root.get("coordinates"),
                    new TypeReference<>() {
                    }
            );
            return new ForecastDemandResponse.Polygon("Polygon", coordinates);
        }
        catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.ANALYTICS_DATA_UNAVAILABLE,
                    "Stored forecast geometry is invalid",
                    Map.of("component", "forecastGeometry")
            );
        }
    }

    private String evaluationStatus(DemandForecastQueryPort.ForecastPointRow row) {
        if (row.actualDemand() == null) {
            return "ACTUAL_PENDING";
        }
        return actualSuppressed(row) ? "ACTUAL_SUPPRESSED" : "ACTUAL_AVAILABLE";
    }

    private boolean actualSuppressed(DemandForecastQueryPort.ForecastPointRow row) {
        return row.actualDemand() != null
                && row.actualDemand() > 0
                && row.actualDemand() < properties.getMinimumActualDemandCount();
    }

    private Integer publishedActualDemand(DemandForecastQueryPort.ForecastPointRow row) {
        return actualSuppressed(row) ? null : row.actualDemand();
    }

    private BigDecimal publishedAbsoluteError(DemandForecastQueryPort.ForecastPointRow row) {
        return actualSuppressed(row) ? null : row.absoluteError();
    }

    private long suppressedActualRows(List<DemandForecastQueryPort.ForecastPointRow> rows) {
        return rows.stream().filter(this::actualSuppressed).count();
    }

    private ForecastEvaluationResponse.Metric evaluationMetric(
            DemandForecastQueryPort.EvaluationMetricRow row
    ) {
        return new ForecastEvaluationResponse.Metric(
                row.forecastRunId(),
                row.modelVersionId(),
                row.modelVersion(),
                row.modelFamily(),
                row.approvalScope(),
                row.foldKey(),
                row.metricName(),
                row.horizonMinutes(),
                row.cellSizeMeters(),
                row.sliceType(),
                row.sliceKey(),
                row.metricValue(),
                row.sampleCount(),
                row.createdAt()
        );
    }

    private ForecastRunResponse forecastRun(DemandForecastQueryPort.ForecastRunHistoryRow row) {
        Freshness freshness = freshness(
                row.runPurpose(),
                row.latestEvaluatedAt(),
                row.publishedAt(),
                row.generatedAt()
        );
        return new ForecastRunResponse(
                row.forecastRunId(),
                row.processingRunId(),
                row.modelVersionId(),
                row.modelVersion(),
                row.modelFamily(),
                row.approvalScope(),
                row.runPurpose(),
                row.status(),
                row.sourceProfile(),
                row.datasetVersion(),
                row.demandEventSemantics(),
                row.gridVersion(),
                row.cellSizeMeters(),
                row.bucketMinutes(),
                row.inferenceCutoff(),
                row.generatedAt(),
                row.finishedAt(),
                row.publishedAt(),
                row.errorCode(),
                row.errorMessage(),
                row.forecastRows(),
                row.evaluatedRows(),
                row.latestEvaluatedAt(),
                freshness.status(),
                freshness.ageSeconds(),
                quality(row.quality())
        );
    }

    private ForecastResponseMetadata forecastMetadata(
            DemandForecastQueryPort.SelectedForecastRunRow run,
            ForecastFilter filter,
            boolean hasRows,
            long suppressedActualRows
    ) {
        Freshness freshness = freshness(
                run.runPurpose(),
                run.latestEvaluatedAt(),
                run.publishedAt(),
                run.generatedAt()
        );
        String availability;
        String reason;
        if (!hasRows) {
            availability = "EMPTY";
            reason = "NO_FORECAST_IN_SELECTED_RANGE";
        }
        else if ("EVALUATION".equals(run.runPurpose())) {
            availability = "AVAILABLE_RESEARCH";
            reason = "HISTORICAL_EVALUATION_NOT_OPERATIONAL";
        }
        else if ("STALE".equals(freshness.status())) {
            availability = "STALE";
            reason = "PUBLISHED_FORECAST_EXCEEDS_FRESHNESS_THRESHOLD";
        }
        else {
            availability = "AVAILABLE";
            reason = null;
        }
        return new ForecastResponseMetadata(
                availability,
                reason,
                freshness.status(),
                freshness.ageSeconds(),
                run.forecastRunId(),
                run.modelVersionId(),
                run.modelVersion(),
                run.featureSetVersion(),
                run.approvalScope(),
                run.sourceProfile(),
                run.datasetVersion(),
                run.demandEventSemantics(),
                run.runPurpose(),
                run.generatedAt(),
                run.inferenceCutoff(),
                freshness.at(),
                filter.from(),
                filter.to(),
                filter.reportingTimezone(),
                filter.cellSizeMeters(),
                filter.horizonMinutes(),
                DEMAND_UNIT,
                run.forecastRows(),
                run.evaluatedRows(),
                properties.getMinimumActualDemandCount(),
                suppressedActualRows,
                quality(run.quality())
        );
    }

    private ForecastResponseMetadata unavailableMetadata(ForecastFilter filter) {
        return new ForecastResponseMetadata(
                "UNAVAILABLE",
                "NO_MATCHING_FORECAST",
                "UNAVAILABLE",
                null,
                filter.forecastRunId(),
                null,
                filter.modelVersion(),
                null,
                null,
                null,
                null,
                null,
                filter.runPurpose(),
                null,
                null,
                null,
                filter.from(),
                filter.to(),
                filter.reportingTimezone(),
                filter.cellSizeMeters(),
                filter.horizonMinutes(),
                DEMAND_UNIT,
                0,
                0,
                properties.getMinimumActualDemandCount(),
                0,
                new ProcessingStatusResponse.QualitySummary(0, 0, 0)
        );
    }

    private Freshness freshness(
            String runPurpose,
            Instant latestEvaluatedAt,
            Instant publishedAt,
            Instant generatedAt
    ) {
        Instant freshnessAt = latestEvaluatedAt != null
                ? latestEvaluatedAt
                : publishedAt != null ? publishedAt : generatedAt;
        if (freshnessAt == null) {
            return new Freshness("UNAVAILABLE", null, null);
        }
        if ("EVALUATION".equals(runPurpose)) {
            return new Freshness("HISTORICAL_EVALUATION", null, freshnessAt);
        }
        long age = ageSeconds(freshnessAt, clock.instant());
        String status = age > properties.getPublishedStaleAfter().toSeconds()
                ? "STALE"
                : "FRESH";
        return new Freshness(status, age, freshnessAt);
    }

    private ForecastFilter normalizeForecastFilter(
            OffsetDateTime from,
            OffsetDateTime to,
            String timezone,
            Integer horizonMinutes,
            Integer cellSizeMeters,
            String modelVersion,
            UUID forecastRunId,
            String runPurpose,
            BigDecimal minLongitude,
            BigDecimal minLatitude,
            BigDecimal maxLongitude,
            BigDecimal maxLatitude
    ) {
        if (from == null) {
            throw validation("from", "from is required and must include an offset");
        }
        if (to == null) {
            throw validation("to", "to is required and must include an offset");
        }
        Instant normalizedFrom = from.toInstant();
        Instant normalizedTo = to.toInstant();
        if (!normalizedFrom.isBefore(normalizedTo)) {
            throw validation("range", "from must be before to");
        }
        if (Duration.between(normalizedFrom, normalizedTo)
                .compareTo(Duration.ofDays(properties.getMaximumRangeDays())) > 0) {
            throw new BusinessException(
                    ErrorCode.ANALYTICS_RANGE_TOO_LARGE,
                    "Forecast range exceeds the configured maximum",
                    Map.of("maximumDays", properties.getMaximumRangeDays())
            );
        }
        String normalizedTimezone = normalizeTimezone(timezone);
        int normalizedHorizon = requireHorizon(horizonMinutes);
        int normalizedCellSize = requireCellSize(cellSizeMeters);
        String normalizedModel = optionalText(modelVersion, "modelVersion", 80);
        String normalizedPurpose = optionalEnum(
                runPurpose,
                "runPurpose",
                FORECAST_PURPOSES
        );
        SpatialBounds bounds = normalizeBounds(
                minLongitude,
                minLatitude,
                maxLongitude,
                maxLatitude
        );
        return new ForecastFilter(
                normalizedFrom,
                normalizedTo,
                normalizedTimezone,
                normalizedHorizon,
                normalizedCellSize,
                normalizedModel,
                forecastRunId,
                normalizedPurpose,
                bounds
        );
    }

    private String normalizeTimezone(String timezone) {
        String normalized = timezone == null || timezone.isBlank()
                ? DEFAULT_TIMEZONE
                : timezone.trim();
        try {
            return ZoneId.of(normalized).getId();
        }
        catch (DateTimeException exception) {
            throw validation("timezone", "timezone must be a supported IANA zone");
        }
    }

    private int requireHorizon(Integer horizonMinutes) {
        if (horizonMinutes == null) {
            throw validation("horizonMinutes", "horizonMinutes is required");
        }
        if (!properties.getAllowedHorizonsMinutes().contains(horizonMinutes)) {
            throw validation(
                    "horizonMinutes",
                    "horizonMinutes must be one of " + properties.getAllowedHorizonsMinutes()
            );
        }
        return horizonMinutes;
    }

    private Integer optionalHorizon(Integer horizonMinutes) {
        return horizonMinutes == null ? null : requireHorizon(horizonMinutes);
    }

    private int requireCellSize(Integer cellSizeMeters) {
        if (cellSizeMeters == null) {
            throw validation("cellSizeMeters", "cellSizeMeters is required");
        }
        if (!spatialProperties.getAllowedCellSizesMeters().contains(cellSizeMeters)) {
            throw validation(
                    "cellSizeMeters",
                    "cellSizeMeters must be one of "
                            + spatialProperties.getAllowedCellSizesMeters()
            );
        }
        return cellSizeMeters;
    }

    private Integer optionalCellSize(Integer cellSizeMeters) {
        return cellSizeMeters == null ? null : requireCellSize(cellSizeMeters);
    }

    private SpatialBounds normalizeBounds(
            BigDecimal minLongitude,
            BigDecimal minLatitude,
            BigDecimal maxLongitude,
            BigDecimal maxLatitude
    ) {
        int provided = (minLongitude == null ? 0 : 1)
                + (minLatitude == null ? 0 : 1)
                + (maxLongitude == null ? 0 : 1)
                + (maxLatitude == null ? 0 : 1);
        if (provided == 0) {
            return null;
        }
        if (provided != 4) {
            throw validation(
                    "bounds",
                    "minLng, minLat, maxLng and maxLat must be provided together"
            );
        }
        if (minLongitude.compareTo(BigDecimal.valueOf(-180)) < 0
                || maxLongitude.compareTo(BigDecimal.valueOf(180)) > 0
                || minLatitude.compareTo(BigDecimal.valueOf(-90)) < 0
                || maxLatitude.compareTo(BigDecimal.valueOf(90)) > 0) {
            throw validation("bounds", "bounding coordinates are outside WGS84 limits");
        }
        if (minLongitude.compareTo(maxLongitude) >= 0
                || minLatitude.compareTo(maxLatitude) >= 0) {
            throw validation("bounds", "minimum coordinates must be less than maximum coordinates");
        }
        double middleLatitude = Math.toRadians(
                (minLatitude.doubleValue() + maxLatitude.doubleValue()) / 2
        );
        double width = EARTH_RADIUS_KM
                * Math.toRadians(maxLongitude.doubleValue() - minLongitude.doubleValue())
                * Math.cos(middleLatitude);
        double height = EARTH_RADIUS_KM
                * Math.toRadians(maxLatitude.doubleValue() - minLatitude.doubleValue());
        if (Math.abs(width * height) > spatialProperties.getMaximumBoundingBoxAreaSquareKm()) {
            throw validation(
                    "bounds",
                    "bounding-box area exceeds "
                            + spatialProperties.getMaximumBoundingBoxAreaSquareKm()
                            + " square kilometres"
            );
        }
        return new SpatialBounds(minLongitude, minLatitude, maxLongitude, maxLatitude);
    }

    private int normalizeHotspotLimit(Integer limit) {
        int normalized = limit == null ? 20 : limit;
        if (normalized <= 0 || normalized > properties.getMaximumHotspots()) {
            throw validation(
                    "limit",
                    "limit must be between 1 and " + properties.getMaximumHotspots()
            );
        }
        return normalized;
    }

    private int normalizePage(int page, int size) {
        if (page < 0) {
            throw validation("page", "page must be zero or greater");
        }
        if (size <= 0 || size > properties.getMaximumPageSize()) {
            throw validation(
                    "size",
                    "size must be between 1 and " + properties.getMaximumPageSize()
            );
        }
        try {
            return Math.multiplyExact(page, size);
        }
        catch (ArithmeticException exception) {
            throw validation("page", "page is too large");
        }
    }

    private String optionalEnum(String value, String field, Set<String> allowed) {
        String normalized = optionalText(value, field, 40);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw validation(field, field + " must be one of " + allowed);
        }
        return normalized;
    }

    private String optionalText(String value, String field, int maximumLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maximumLength) {
            throw validation(field, field + " must not exceed " + maximumLength + " characters");
        }
        return normalized;
    }

    private ProcessingStatusResponse.QualitySummary quality(
            DemandForecastQueryPort.QualityCounts counts
    ) {
        return new ProcessingStatusResponse.QualitySummary(
                counts.pass(),
                counts.warn(),
                counts.fail()
        );
    }

    private long ageSeconds(Instant reference, Instant now) {
        return Math.max(0, Duration.between(reference, now).toSeconds());
    }

    private Long nullableAgeSeconds(Instant reference, Instant now) {
        return reference == null ? null : ageSeconds(reference, now);
    }

    private <T> T database(String component, Supplier<T> query) {
        try {
            return query.get();
        }
        catch (BusinessException exception) {
            throw exception;
        }
        catch (DataAccessException exception) {
            log.warn(
                    "Demand forecasting read failed component={} errorType={}",
                    component,
                    exception.getClass().getSimpleName()
            );
            BusinessException unavailable = new BusinessException(
                    ErrorCode.ANALYTICS_DATA_UNAVAILABLE,
                    "Demand forecasting analytics data is unavailable",
                    Map.of("component", component)
            );
            unavailable.initCause(exception);
            throw unavailable;
        }
    }

    private BusinessException validation(String field, String message) {
        return new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR.defaultMessage(),
                Map.of(field, message)
        );
    }

    private record ForecastFilter(
            Instant from,
            Instant to,
            String reportingTimezone,
            int horizonMinutes,
            int cellSizeMeters,
            String modelVersion,
            UUID forecastRunId,
            String runPurpose,
            SpatialBounds bounds
    ) {
    }

    private record Freshness(String status, Long ageSeconds, Instant at) {
    }
}
