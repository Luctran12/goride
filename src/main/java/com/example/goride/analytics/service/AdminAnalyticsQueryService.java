package com.example.goride.analytics.service;

import com.example.goride.analytics.config.AnalyticsSpatialProperties;
import com.example.goride.analytics.config.AnalyticsTelemetryProperties;
import com.example.goride.analytics.dto.AnalyticsOverviewResponse;
import com.example.goride.analytics.dto.DemandHeatmapResponse;
import com.example.goride.analytics.dto.DemandTimeseriesResponse;
import com.example.goride.analytics.dto.MatchingFunnelResponse;
import com.example.goride.analytics.dto.MatchingPerformanceResponse;
import com.example.goride.analytics.dto.SupplyTimeseriesResponse;
import com.example.goride.analytics.model.AnalyticsBucket;
import com.example.goride.analytics.model.AnalyticsCountUnit;
import com.example.goride.analytics.model.AnalyticsFilter;
import com.example.goride.analytics.model.AnalyticsQueryOperation;
import com.example.goride.analytics.model.MatchingFunnelStepName;
import com.example.goride.analytics.model.SpatialBounds;
import com.example.goride.analytics.repository.DirectAnalyticsQueryPort.DemandBucketStats;
import com.example.goride.analytics.repository.DirectAnalyticsQueryPort.FunnelStats;
import com.example.goride.analytics.repository.DirectAnalyticsQueryPort.MatchingPerformanceStats;
import com.example.goride.analytics.repository.DirectAnalyticsQueryPort.OverviewStats;
import com.example.goride.analytics.repository.DirectAnalyticsQueryPort.SpatialCellStats;
import com.example.goride.analytics.repository.DirectAnalyticsQueryPort.SupplyBucketStats;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.driver.domain.VehicleType;
import com.example.goride.servicearea.repository.ServiceAreaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true, timeout = 15, isolation = Isolation.REPEATABLE_READ)
public class AdminAnalyticsQueryService {
    public static final String DEFAULT_TIMEZONE = "Asia/Ho_Chi_Minh";
    private static final Duration MAX_GENERAL_RANGE = Duration.ofDays(366);
    private static final BigDecimal MINIMUM_SUPPLY_COVERAGE = new BigDecimal("0.80");
    private static final int RATIO_SCALE = 4;
    private static final int AVERAGE_SCALE = 2;
    private static final double EARTH_RADIUS_KM = 6371.0088;

    private final AnalyticsQueryRouter queryRouter;
    private final ServiceAreaRepository serviceAreaRepository;
    private final AnalyticsTelemetryProperties telemetryProperties;
    private final AnalyticsSpatialProperties spatialProperties;
    private final Clock clock;

    public AdminAnalyticsQueryService(
            AnalyticsQueryRouter queryRouter,
            ServiceAreaRepository serviceAreaRepository,
            AnalyticsTelemetryProperties telemetryProperties,
            AnalyticsSpatialProperties spatialProperties,
            Clock clock
    ) {
        this.queryRouter = queryRouter;
        this.serviceAreaRepository = serviceAreaRepository;
        this.telemetryProperties = telemetryProperties;
        this.spatialProperties = spatialProperties;
        this.clock = clock;
    }

    public AnalyticsOverviewResponse getOverview(
            OffsetDateTime from,
            OffsetDateTime to,
            String timezone,
            VehicleType vehicleType,
            Long serviceAreaId
    ) {
        Instant freshnessAt = clock.instant();
        AnalyticsFilter filter = normalizeFilter(from, to, timezone, vehicleType, serviceAreaId);
        AnalyticsQueryRouter.Selection selection = queryRouter.select(
                filter,
                AnalyticsQueryOperation.OVERVIEW,
                null,
                freshnessAt
        );
        OverviewStats stats = selection.queryPort().overview(filter);
        return new AnalyticsOverviewResponse(
                filter.from(),
                filter.to(),
                filter.reportingTimezone().getId(),
                selection.sourceVariant(),
                selection.freshnessAt(),
                stats.tripRequests(),
                stats.completedTrips(),
                stats.completedTripsByRequestCohort(),
                stats.cancelledTrips(),
                stats.noDriverTrips(),
                ratio(stats.completedTripsByRequestCohort(), stats.tripRequests()),
                stats.completedPayments(),
                zeroIfNull(stats.completedRevenue()),
                stats.matchingRuns(),
                stats.terminalRuns(),
                ratio(stats.matchedRuns(), stats.terminalRuns()),
                duration(stats.averageMatchingDurationMs()),
                duration(stats.p50MatchingDurationMs()),
                duration(stats.p95MatchingDurationMs())
        );
    }

    public DemandTimeseriesResponse getDemandTimeseries(
            OffsetDateTime from,
            OffsetDateTime to,
            String timezone,
            VehicleType vehicleType,
            Long serviceAreaId,
            AnalyticsBucket bucket
    ) {
        Instant freshnessAt = clock.instant();
        AnalyticsFilter filter = normalizeFilter(from, to, timezone, vehicleType, serviceAreaId);
        AnalyticsBucket normalizedBucket = requireBucket(bucket);
        AnalyticsQueryRouter.Selection selection = queryRouter.select(
                filter,
                AnalyticsQueryOperation.DEMAND_TIMESERIES,
                normalizedBucket,
                freshnessAt
        );
        Map<LocalDateTime, DemandBucketStats> statsByBucket = indexDemand(
                selection.queryPort().demandTimeseries(filter, normalizedBucket)
        );
        List<DemandTimeseriesResponse.Point> points = bucketStarts(filter, normalizedBucket).stream()
                .map(bucketStart -> {
                    DemandBucketStats stats = statsByBucket.get(bucketStart.toLocalDateTime());
                    long tripRequests = stats == null ? 0 : stats.tripRequests();
                    long completed = stats == null ? 0 : stats.completedTripsByRequestCohort();
                    return new DemandTimeseriesResponse.Point(
                            bucketStart.toOffsetDateTime(),
                            tripRequests,
                            completed,
                            ratio(completed, tripRequests)
                    );
                })
                .toList();
        return new DemandTimeseriesResponse(
                filter.from(),
                filter.to(),
                filter.reportingTimezone().getId(),
                normalizedBucket,
                selection.sourceVariant(),
                selection.freshnessAt(),
                points
        );
    }

    public SupplyTimeseriesResponse getSupplyTimeseries(
            OffsetDateTime from,
            OffsetDateTime to,
            String timezone,
            VehicleType vehicleType,
            Long serviceAreaId,
            AnalyticsBucket bucket
    ) {
        Instant freshnessAt = clock.instant();
        AnalyticsFilter filter = normalizeFilter(from, to, timezone, vehicleType, serviceAreaId);
        AnalyticsBucket normalizedBucket = requireBucket(bucket);
        if (!normalizedBucket.supportsSupply()) {
            throw validation("bucket", "Supply timeseries supports only HOUR or DAY");
        }
        AnalyticsQueryRouter.Selection selection = queryRouter.select(
                filter,
                AnalyticsQueryOperation.SUPPLY_TIMESERIES,
                normalizedBucket,
                freshnessAt
        );

        Map<LocalDateTime, SupplyBucketStats> supplyByBucket = selection.queryPort()
                .supplyTimeseries(filter, normalizedBucket)
                .stream()
                .collect(LinkedHashMap::new, (map, stats) -> map.put(stats.bucketStart(), stats), Map::putAll);
        Map<LocalDateTime, DemandBucketStats> demandByBucket = indexDemand(
                selection.queryPort().demandTimeseries(filter, normalizedBucket)
        );
        Map<LocalDateTime, Long> expectedByBucket = expectedSnapshotBuckets(
                filter,
                normalizedBucket
        );

        List<SupplyTimeseriesResponse.Point> points = bucketStarts(filter, normalizedBucket).stream()
                .map(bucketStart -> supplyPoint(
                        bucketStart,
                        supplyByBucket.get(bucketStart.toLocalDateTime()),
                        demandByBucket.get(bucketStart.toLocalDateTime()),
                        expectedByBucket.getOrDefault(bucketStart.toLocalDateTime(), 0L)
                ))
                .toList();
        return new SupplyTimeseriesResponse(
                filter.from(),
                filter.to(),
                filter.reportingTimezone().getId(),
                normalizedBucket,
                selection.sourceVariant(),
                selection.freshnessAt(),
                points
        );
    }

    public DemandHeatmapResponse getDemandHeatmap(
            OffsetDateTime from,
            OffsetDateTime to,
            String timezone,
            VehicleType vehicleType,
            Long serviceAreaId,
            Integer cellSizeMeters,
            BigDecimal minLongitude,
            BigDecimal minLatitude,
            BigDecimal maxLongitude,
            BigDecimal maxLatitude
    ) {
        Instant freshnessAt = clock.instant();
        AnalyticsFilter filter = normalizeFilter(from, to, timezone, vehicleType, serviceAreaId);
        validateHeatmapRange(filter);
        int normalizedCellSize = normalizeCellSize(cellSizeMeters);
        SpatialBounds bounds = normalizeBounds(
                minLongitude,
                minLatitude,
                maxLongitude,
                maxLatitude
        );
        int maximumCells = spatialProperties.getMaximumCells();
        AnalyticsQueryRouter.Selection selection = queryRouter.select(
                filter,
                AnalyticsQueryOperation.DEMAND_HEATMAP,
                null,
                freshnessAt,
                bounds == null
        );
        List<SpatialCellStats> cells = selection.queryPort().demandHeatmap(
                filter,
                normalizedCellSize,
                spatialProperties.getProjectedSrid(),
                bounds,
                maximumCells + 1
        );
        if (cells.size() > maximumCells) {
            throw new BusinessException(
                    ErrorCode.ANALYTICS_RESULT_TOO_LARGE,
                    "Heatmap exceeds the maximum number of cells",
                    Map.of("maximumCells", maximumCells)
            );
        }
        List<DemandHeatmapResponse.Feature> features = cells.stream()
                .map(cell -> new DemandHeatmapResponse.Feature(
                        "Feature",
                        new DemandHeatmapResponse.Polygon(
                                "Polygon",
                                List.of(cell.exteriorRing())
                        ),
                        new DemandHeatmapResponse.Properties(
                                cell.cellId(),
                                cell.tripRequests(),
                                cell.completedTripsByRequestCohort(),
                                ratio(
                                        cell.completedTripsByRequestCohort(),
                                        cell.tripRequests()
                                )
                        )
                ))
                .toList();
        return new DemandHeatmapResponse(
                "FeatureCollection",
                new DemandHeatmapResponse.Metadata(
                        filter.from(),
                        filter.to(),
                        filter.reportingTimezone().getId(),
                        normalizedCellSize,
                        selection.sourceVariant(),
                        selection.freshnessAt()
                ),
                features
        );
    }

    public MatchingPerformanceResponse getMatchingPerformance(
            OffsetDateTime from,
            OffsetDateTime to,
            String timezone,
            VehicleType vehicleType,
            Long serviceAreaId
    ) {
        Instant freshnessAt = clock.instant();
        AnalyticsFilter filter = normalizeFilter(from, to, timezone, vehicleType, serviceAreaId);
        AnalyticsQueryRouter.Selection selection = queryRouter.select(
                filter,
                AnalyticsQueryOperation.MATCHING_PERFORMANCE,
                null,
                freshnessAt
        );
        MatchingPerformanceStats stats = selection.queryPort().matchingPerformance(filter);
        return new MatchingPerformanceResponse(
                filter.from(),
                filter.to(),
                filter.reportingTimezone().getId(),
                selection.sourceVariant(),
                selection.freshnessAt(),
                stats.matchingRuns(),
                stats.terminalRuns(),
                stats.matchedRuns(),
                stats.noDriverRuns(),
                stats.cancelledRuns(),
                stats.failedRuns(),
                ratio(stats.matchedRuns(), stats.terminalRuns()),
                duration(stats.averageMatchingDurationMs()),
                duration(stats.p50MatchingDurationMs()),
                duration(stats.p95MatchingDurationMs()),
                average(stats.averageSearchesPerRun()),
                average(stats.averageCandidatesPerRun()),
                average(stats.averageOffersPerRun()),
                ratio(stats.acceptedOffers(), stats.terminalOffers()),
                ratio(stats.rejectedOffers(), stats.terminalOffers()),
                ratio(stats.timedOutOffers(), stats.terminalOffers()),
                average(stats.averageCandidateDistanceM())
        );
    }

    public MatchingFunnelResponse getMatchingFunnel(
            OffsetDateTime from,
            OffsetDateTime to,
            String timezone,
            VehicleType vehicleType,
            Long serviceAreaId
    ) {
        Instant freshnessAt = clock.instant();
        AnalyticsFilter filter = normalizeFilter(from, to, timezone, vehicleType, serviceAreaId);
        AnalyticsQueryRouter.Selection selection = queryRouter.select(
                filter,
                AnalyticsQueryOperation.MATCHING_FUNNEL,
                null,
                freshnessAt
        );
        FunnelStats stats = selection.queryPort().matchingFunnel(filter);
        List<MatchingFunnelResponse.Step> steps = List.of(
                new MatchingFunnelResponse.Step(
                        MatchingFunnelStepName.RUN_STARTED,
                        AnalyticsCountUnit.RUN,
                        stats.runStarted()
                ),
                new MatchingFunnelResponse.Step(
                        MatchingFunnelStepName.CANDIDATE_FOUND,
                        AnalyticsCountUnit.RUN,
                        stats.candidateFound()
                ),
                new MatchingFunnelResponse.Step(
                        MatchingFunnelStepName.OFFER_SENT,
                        AnalyticsCountUnit.RUN,
                        stats.offerSent()
                ),
                new MatchingFunnelResponse.Step(
                        MatchingFunnelStepName.OFFER_ACCEPTED,
                        AnalyticsCountUnit.RUN,
                        stats.offerAccepted()
                ),
                new MatchingFunnelResponse.Step(
                        MatchingFunnelStepName.TRIP_COMPLETED,
                        AnalyticsCountUnit.TRIP,
                        stats.tripCompleted()
                )
        );
        return new MatchingFunnelResponse(
                filter.from(),
                filter.to(),
                filter.reportingTimezone().getId(),
                selection.sourceVariant(),
                selection.freshnessAt(),
                steps
        );
    }

    private AnalyticsFilter normalizeFilter(
            OffsetDateTime from,
            OffsetDateTime to,
            String timezone,
            VehicleType vehicleType,
            Long serviceAreaId
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
        Duration range = Duration.between(normalizedFrom, normalizedTo);
        if (range.compareTo(MAX_GENERAL_RANGE) > 0) {
            throw new BusinessException(
                    ErrorCode.ANALYTICS_RANGE_TOO_LARGE,
                    "Analytics range must not exceed 366 days",
                    Map.of("maximumDays", 366)
            );
        }
        ZoneId reportingTimezone;
        try {
            reportingTimezone = ZoneId.of(
                    timezone == null || timezone.isBlank() ? DEFAULT_TIMEZONE : timezone.trim()
            );
        } catch (DateTimeException exception) {
            throw validation("timezone", "timezone must be a supported IANA zone");
        }
        if (serviceAreaId != null) {
            if (serviceAreaId <= 0) {
                throw validation("serviceAreaId", "serviceAreaId must be positive");
            }
            if (!serviceAreaRepository.existsById(serviceAreaId)) {
                throw new BusinessException(
                        ErrorCode.SERVICE_AREA_NOT_FOUND,
                        "Service area not found",
                        Map.of("serviceAreaId", serviceAreaId)
                );
            }
        }
        return new AnalyticsFilter(
                normalizedFrom,
                normalizedTo,
                reportingTimezone,
                vehicleType,
                serviceAreaId
        );
    }

    private SupplyTimeseriesResponse.Point supplyPoint(
            ZonedDateTime bucketStart,
            SupplyBucketStats supply,
            DemandBucketStats demand,
            long expectedBuckets
    ) {
        long tripRequests = demand == null ? 0 : demand.tripRequests();
        BigDecimal coverage = expectedBuckets == 0
                ? null
                : ratio(supply == null ? 0 : supply.observedBuckets(), expectedBuckets);
        BigDecimal available = supply == null ? null : average(supply.averageAvailableDrivers());
        BigDecimal requestToAvailableRatio = null;
        if (coverage != null
                && coverage.compareTo(MINIMUM_SUPPLY_COVERAGE) >= 0
                && available != null
                && available.signum() > 0) {
            requestToAvailableRatio = BigDecimal.valueOf(tripRequests)
                    .divide(available, RATIO_SCALE, RoundingMode.HALF_UP);
        }
        return new SupplyTimeseriesResponse.Point(
                bucketStart.toOffsetDateTime(),
                supply == null ? null : average(supply.averageOnlineDrivers()),
                available,
                supply == null ? null : average(supply.averageBusyDrivers()),
                coverage,
                tripRequests,
                requestToAvailableRatio
        );
    }

    private void validateHeatmapRange(AnalyticsFilter filter) {
        int maximumDays = spatialProperties.getMaximumRangeDays();
        if (Duration.between(filter.from(), filter.to())
                .compareTo(Duration.ofDays(maximumDays)) > 0) {
            throw new BusinessException(
                    ErrorCode.ANALYTICS_RANGE_TOO_LARGE,
                    "Heatmap range must not exceed " + maximumDays + " days",
                    Map.of("maximumDays", maximumDays)
            );
        }
    }

    private int normalizeCellSize(Integer cellSizeMeters) {
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

    private SpatialBounds normalizeBounds(
            BigDecimal minLongitude,
            BigDecimal minLatitude,
            BigDecimal maxLongitude,
            BigDecimal maxLatitude
    ) {
        int provided = 0;
        provided += minLongitude == null ? 0 : 1;
        provided += minLatitude == null ? 0 : 1;
        provided += maxLongitude == null ? 0 : 1;
        provided += maxLatitude == null ? 0 : 1;
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
        double areaSquareKm = boundingBoxAreaSquareKm(
                minLongitude.doubleValue(),
                minLatitude.doubleValue(),
                maxLongitude.doubleValue(),
                maxLatitude.doubleValue()
        );
        if (areaSquareKm > spatialProperties.getMaximumBoundingBoxAreaSquareKm()) {
            throw validation(
                    "bounds",
                    "bounding-box area exceeds "
                            + spatialProperties.getMaximumBoundingBoxAreaSquareKm()
                            + " square kilometres"
            );
        }
        return new SpatialBounds(
                minLongitude,
                minLatitude,
                maxLongitude,
                maxLatitude
        );
    }

    private double boundingBoxAreaSquareKm(
            double minLongitude,
            double minLatitude,
            double maxLongitude,
            double maxLatitude
    ) {
        double middleLatitudeRadians = Math.toRadians((minLatitude + maxLatitude) / 2);
        double widthKm = EARTH_RADIUS_KM
                * Math.toRadians(maxLongitude - minLongitude)
                * Math.cos(middleLatitudeRadians);
        double heightKm = EARTH_RADIUS_KM * Math.toRadians(maxLatitude - minLatitude);
        return Math.abs(widthKm * heightKm);
    }

    private Map<LocalDateTime, DemandBucketStats> indexDemand(List<DemandBucketStats> stats) {
        Map<LocalDateTime, DemandBucketStats> result = new HashMap<>();
        stats.forEach(point -> result.put(point.bucketStart(), point));
        return result;
    }

    private List<ZonedDateTime> bucketStarts(AnalyticsFilter filter, AnalyticsBucket bucket) {
        List<ZonedDateTime> starts = new ArrayList<>();
        ZonedDateTime cursor = truncate(filter.from().atZone(filter.reportingTimezone()), bucket);
        while (cursor.toInstant().isBefore(filter.to())) {
            starts.add(cursor);
            cursor = advance(cursor, bucket);
        }
        return List.copyOf(starts);
    }

    private Map<LocalDateTime, Long> expectedSnapshotBuckets(
            AnalyticsFilter filter,
            AnalyticsBucket bucket
    ) {
        long intervalSeconds = telemetryProperties.getSupplySnapshotIntervalSeconds();
        long firstEpochSecond = filter.from().getEpochSecond();
        if (filter.from().getNano() > 0) {
            firstEpochSecond++;
        }
        long remainder = Math.floorMod(firstEpochSecond, intervalSeconds);
        if (remainder != 0) {
            firstEpochSecond += intervalSeconds - remainder;
        }

        Map<LocalDateTime, Long> expected = new HashMap<>();
        Instant cursor = Instant.ofEpochSecond(firstEpochSecond);
        while (cursor.isBefore(filter.to())) {
            LocalDateTime bucketStart = truncate(
                    cursor.atZone(filter.reportingTimezone()),
                    bucket
            ).toLocalDateTime();
            expected.merge(bucketStart, 1L, Long::sum);
            cursor = cursor.plusSeconds(intervalSeconds);
        }
        return expected;
    }

    private ZonedDateTime truncate(ZonedDateTime value, AnalyticsBucket bucket) {
        return switch (bucket) {
            case HOUR -> value.truncatedTo(ChronoUnit.HOURS);
            case DAY -> value.truncatedTo(ChronoUnit.DAYS);
            case WEEK -> value
                    .with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                    .truncatedTo(ChronoUnit.DAYS);
        };
    }

    private ZonedDateTime advance(ZonedDateTime value, AnalyticsBucket bucket) {
        return switch (bucket) {
            case HOUR -> value.plusHours(1);
            case DAY -> value.plusDays(1);
            case WEEK -> value.plusWeeks(1);
        };
    }

    private AnalyticsBucket requireBucket(AnalyticsBucket bucket) {
        if (bucket == null) {
            throw validation("bucket", "bucket is required");
        }
        return bucket;
    }

    private BigDecimal ratio(long numerator, long denominator) {
        if (denominator == 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), RATIO_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal average(BigDecimal value) {
        return value == null ? null : value.setScale(AVERAGE_SCALE, RoundingMode.HALF_UP);
    }

    private Long duration(BigDecimal value) {
        return value == null ? null : value.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BusinessException validation(String field, String message) {
        return new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR.defaultMessage(),
                Map.of(field, message)
        );
    }
}
