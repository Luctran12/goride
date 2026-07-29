package com.example.goride.analytics.service;

import com.example.goride.analytics.config.AnalyticsSpatialProperties;
import com.example.goride.analytics.config.AnalyticsTelemetryProperties;
import com.example.goride.analytics.model.AnalyticsBucket;
import com.example.goride.analytics.model.SpatialBounds;
import com.example.goride.analytics.repository.DirectAnalyticsQueryPort;
import com.example.goride.analytics.repository.DirectAnalyticsQueryPort.DemandBucketStats;
import com.example.goride.analytics.repository.DirectAnalyticsQueryPort.FunnelStats;
import com.example.goride.analytics.repository.DirectAnalyticsQueryPort.MatchingPerformanceStats;
import com.example.goride.analytics.repository.DirectAnalyticsQueryPort.OverviewStats;
import com.example.goride.analytics.repository.DirectAnalyticsQueryPort.SpatialCellStats;
import com.example.goride.analytics.repository.DirectAnalyticsQueryPort.SupplyBucketStats;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.servicearea.repository.ServiceAreaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAnalyticsQueryServiceTests {
    private static final Instant FRESHNESS_AT = Instant.parse("2026-07-28T03:00:00Z");

    @Mock
    private DirectAnalyticsQueryPort queryPort;

    @Mock
    private ServiceAreaRepository serviceAreaRepository;

    private AdminAnalyticsQueryService service;
    private AnalyticsSpatialProperties spatialProperties;

    @BeforeEach
    void setUp() {
        AnalyticsTelemetryProperties telemetryProperties = new AnalyticsTelemetryProperties();
        telemetryProperties.setSupplySnapshotIntervalSeconds(300);
        spatialProperties = new AnalyticsSpatialProperties();
        service = new AdminAnalyticsQueryService(
                queryPort,
                serviceAreaRepository,
                telemetryProperties,
                spatialProperties,
                Clock.fixed(FRESHNESS_AT, ZoneOffset.UTC)
        );
    }

    @Test
    void mapsOverviewWithCohortRatesAndCompletedPaymentRevenue() {
        when(queryPort.overview(any())).thenReturn(new OverviewStats(
                10,
                7,
                8,
                1,
                1,
                7,
                new BigDecimal("350000"),
                5,
                4,
                3,
                new BigDecimal("1250.4"),
                new BigDecimal("1000"),
                new BigDecimal("2500")
        ));

        var response = service.getOverview(
                offset("2026-07-01T00:00:00+07:00"),
                offset("2026-07-02T00:00:00+07:00"),
                "Asia/Ho_Chi_Minh",
                null,
                null
        );

        assertThat(response.from()).isEqualTo(Instant.parse("2026-06-30T17:00:00Z"));
        assertThat(response.to()).isEqualTo(Instant.parse("2026-07-01T17:00:00Z"));
        assertThat(response.dataFreshnessAt()).isEqualTo(FRESHNESS_AT);
        assertThat(response.completionRate()).isEqualByComparingTo("0.8000");
        assertThat(response.matchingSuccessRate()).isEqualByComparingTo("0.7500");
        assertThat(response.completedRevenue()).isEqualByComparingTo("350000");
        assertThat(response.averageMatchingDurationMs()).isEqualTo(1250);
    }

    @Test
    void demandTimeseriesReturnsContinuousReportingTimezoneBuckets() {
        when(queryPort.demandTimeseries(any(), eq(AnalyticsBucket.HOUR))).thenReturn(List.of(
                new DemandBucketStats(
                        LocalDateTime.parse("2026-07-01T01:00:00"),
                        4,
                        3
                )
        ));

        var response = service.getDemandTimeseries(
                offset("2026-07-01T00:00:00+07:00"),
                offset("2026-07-01T03:00:00+07:00"),
                "Asia/Ho_Chi_Minh",
                null,
                null,
                AnalyticsBucket.HOUR
        );

        assertThat(response.points()).hasSize(3);
        assertThat(response.points().get(0).tripRequests()).isZero();
        assertThat(response.points().get(0).completionRate()).isNull();
        assertThat(response.points().get(1).bucketStart())
                .isEqualTo(offset("2026-07-01T01:00:00+07:00"));
        assertThat(response.points().get(1).completionRate()).isEqualByComparingTo("0.7500");
        assertThat(response.points().get(2).tripRequests()).isZero();
    }

    @Test
    void supplyTimeseriesUsesExpectedFiveMinuteBucketsAndCoverageGuard() {
        when(queryPort.supplyTimeseries(any(), eq(AnalyticsBucket.HOUR))).thenReturn(List.of(
                new SupplyBucketStats(
                        LocalDateTime.parse("2026-07-01T00:00:00"),
                        new BigDecimal("80"),
                        new BigDecimal("50"),
                        new BigDecimal("30"),
                        12
                ),
                new SupplyBucketStats(
                        LocalDateTime.parse("2026-07-01T01:00:00"),
                        new BigDecimal("70"),
                        new BigDecimal("40"),
                        new BigDecimal("30"),
                        9
                )
        ));
        when(queryPort.demandTimeseries(any(), eq(AnalyticsBucket.HOUR))).thenReturn(List.of(
                new DemandBucketStats(LocalDateTime.parse("2026-07-01T00:00:00"), 100, 80),
                new DemandBucketStats(LocalDateTime.parse("2026-07-01T01:00:00"), 80, 60)
        ));

        var response = service.getSupplyTimeseries(
                offset("2026-07-01T00:00:00+07:00"),
                offset("2026-07-01T02:00:00+07:00"),
                "Asia/Ho_Chi_Minh",
                null,
                null,
                AnalyticsBucket.HOUR
        );

        assertThat(response.points()).hasSize(2);
        assertThat(response.points().get(0).snapshotCoverage()).isEqualByComparingTo("1.0000");
        assertThat(response.points().get(0).requestToAvailableDriverRatio())
                .isEqualByComparingTo("2.0000");
        assertThat(response.points().get(1).snapshotCoverage()).isEqualByComparingTo("0.7500");
        assertThat(response.points().get(1).requestToAvailableDriverRatio()).isNull();
    }

    @Test
    void mapsMatchingPerformanceWithExplicitTerminalDenominators() {
        when(queryPort.matchingPerformance(any())).thenReturn(new MatchingPerformanceStats(
                10,
                8,
                6,
                1,
                1,
                0,
                new BigDecimal("1000"),
                new BigDecimal("800"),
                new BigDecimal("2200"),
                new BigDecimal("1.5"),
                new BigDecimal("4.25"),
                new BigDecimal("1.75"),
                10,
                6,
                3,
                1,
                new BigDecimal("1450.255")
        ));

        var response = service.getMatchingPerformance(
                offset("2026-07-01T00:00:00Z"),
                offset("2026-07-02T00:00:00Z"),
                "UTC",
                null,
                null
        );

        assertThat(response.matchingSuccessRate()).isEqualByComparingTo("0.7500");
        assertThat(response.offerAcceptanceRate()).isEqualByComparingTo("0.6000");
        assertThat(response.offerRejectionRate()).isEqualByComparingTo("0.3000");
        assertThat(response.offerTimeoutRate()).isEqualByComparingTo("0.1000");
        assertThat(response.averageCandidateDistanceM()).isEqualByComparingTo("1450.26");
    }

    @Test
    void heatmapMapsGeoJsonAndPassesBoundedSpatialQuery() {
        when(queryPort.demandHeatmap(
                any(),
                eq(1000),
                eq(32648),
                any(),
                eq(5001)
        )).thenReturn(List.of(new SpatialCellStats(
                "32648:1000:686:1191",
                List.of(
                        coordinate("106.60", "10.70"),
                        coordinate("106.61", "10.70"),
                        coordinate("106.61", "10.71"),
                        coordinate("106.60", "10.71"),
                        coordinate("106.60", "10.70")
                ),
                5,
                4
        )));

        var response = service.getDemandHeatmap(
                offset("2026-07-01T00:00:00Z"),
                offset("2026-07-08T00:00:00Z"),
                "UTC",
                null,
                null,
                1000,
                new BigDecimal("106.5"),
                new BigDecimal("10.6"),
                new BigDecimal("106.9"),
                new BigDecimal("10.9")
        );

        assertThat(response.type()).isEqualTo("FeatureCollection");
        assertThat(response.metadata().cellSizeMeters()).isEqualTo(1000);
        assertThat(response.features()).singleElement().satisfies(feature -> {
            assertThat(feature.type()).isEqualTo("Feature");
            assertThat(feature.geometry().type()).isEqualTo("Polygon");
            assertThat(feature.geometry().coordinates().get(0)).hasSize(5);
            assertThat(feature.properties().cellId()).isEqualTo("32648:1000:686:1191");
            assertThat(feature.properties().completionRate()).isEqualByComparingTo("0.8000");
        });

        ArgumentCaptor<SpatialBounds> boundsCaptor = ArgumentCaptor.forClass(SpatialBounds.class);
        verify(queryPort).demandHeatmap(
                any(),
                eq(1000),
                eq(32648),
                boundsCaptor.capture(),
                eq(5001)
        );
        assertThat(boundsCaptor.getValue().minLongitude()).isEqualByComparingTo("106.5");
    }

    @Test
    void heatmapRejectsUnsafeRangeCellBoundsAndPayload() {
        assertThatThrownBy(() -> service.getDemandHeatmap(
                offset("2026-07-01T00:00:00Z"),
                offset("2026-08-02T00:00:00Z"),
                "UTC",
                null,
                null,
                1000,
                null,
                null,
                null,
                null
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.ANALYTICS_RANGE_TOO_LARGE));

        assertThatThrownBy(() -> service.getDemandHeatmap(
                offset("2026-07-01T00:00:00Z"),
                offset("2026-07-02T00:00:00Z"),
                "UTC",
                null,
                null,
                300,
                null,
                null,
                null,
                null
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        assertThatThrownBy(() -> service.getDemandHeatmap(
                offset("2026-07-01T00:00:00Z"),
                offset("2026-07-02T00:00:00Z"),
                "UTC",
                null,
                null,
                1000,
                new BigDecimal("106.5"),
                null,
                new BigDecimal("106.9"),
                new BigDecimal("10.9")
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        spatialProperties.setMaximumCells(1);
        when(queryPort.demandHeatmap(any(), eq(1000), eq(32648), eq(null), eq(2)))
                .thenReturn(List.of(emptyCell("first"), emptyCell("second")));
        assertThatThrownBy(() -> service.getDemandHeatmap(
                offset("2026-07-01T00:00:00Z"),
                offset("2026-07-02T00:00:00Z"),
                "UTC",
                null,
                null,
                1000,
                null,
                null,
                null,
                null
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.ANALYTICS_RESULT_TOO_LARGE));
    }

    @Test
    void funnelKeepsRunAndTripUnitsExplicit() {
        when(queryPort.matchingFunnel(any())).thenReturn(new FunnelStats(10, 9, 8, 7, 6));

        var response = service.getMatchingFunnel(
                offset("2026-07-01T00:00:00Z"),
                offset("2026-07-02T00:00:00Z"),
                "UTC",
                null,
                null
        );

        assertThat(response.steps()).extracting(step -> step.unit())
                .containsExactly("RUN", "RUN", "RUN", "RUN", "TRIP");
        assertThat(response.steps()).extracting(step -> step.count())
                .containsExactly(10L, 9L, 8L, 7L, 6L);
    }

    @Test
    void normalizesServiceAreaAndPreservesHalfOpenRange() {
        when(serviceAreaRepository.existsById(12L)).thenReturn(true);
        when(queryPort.overview(any())).thenReturn(emptyOverview());

        service.getOverview(
                offset("2026-07-01T00:00:00+07:00"),
                offset("2026-07-02T00:00:00+07:00"),
                "Asia/Ho_Chi_Minh",
                null,
                12L
        );

        ArgumentCaptor<com.example.goride.analytics.model.AnalyticsFilter> captor =
                ArgumentCaptor.forClass(com.example.goride.analytics.model.AnalyticsFilter.class);
        verify(queryPort).overview(captor.capture());
        assertThat(captor.getValue().from()).isEqualTo(Instant.parse("2026-06-30T17:00:00Z"));
        assertThat(captor.getValue().to()).isEqualTo(Instant.parse("2026-07-01T17:00:00Z"));
        assertThat(captor.getValue().serviceAreaId()).isEqualTo(12L);
    }

    @Test
    void rejectsInvalidRangeTimezoneUnknownAreaAndUnsupportedSupplyBucket() {
        assertThatThrownBy(() -> service.getOverview(
                offset("2026-07-02T00:00:00Z"),
                offset("2026-07-01T00:00:00Z"),
                "UTC",
                null,
                null
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        assertThatThrownBy(() -> service.getOverview(
                offset("2026-01-01T00:00:00Z"),
                offset("2027-01-03T00:00:00Z"),
                "UTC",
                null,
                null
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.ANALYTICS_RANGE_TOO_LARGE));

        assertThatThrownBy(() -> service.getOverview(
                offset("2026-07-01T00:00:00Z"),
                offset("2026-07-02T00:00:00Z"),
                "Mars/Olympus",
                null,
                null
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        when(serviceAreaRepository.existsById(999L)).thenReturn(false);
        assertThatThrownBy(() -> service.getOverview(
                offset("2026-07-01T00:00:00Z"),
                offset("2026-07-02T00:00:00Z"),
                "UTC",
                null,
                999L
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.SERVICE_AREA_NOT_FOUND));

        assertThatThrownBy(() -> service.getSupplyTimeseries(
                offset("2026-07-01T00:00:00Z"),
                offset("2026-07-02T00:00:00Z"),
                "UTC",
                null,
                null,
                AnalyticsBucket.WEEK
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    private OverviewStats emptyOverview() {
        return new OverviewStats(
                0,
                0,
                0,
                0,
                0,
                0,
                BigDecimal.ZERO,
                0,
                0,
                0,
                null,
                null,
                null
        );
    }

    private SpatialCellStats emptyCell(String cellId) {
        return new SpatialCellStats(
                cellId,
                List.of(
                        coordinate("0", "0"),
                        coordinate("1", "0"),
                        coordinate("1", "1"),
                        coordinate("0", "1"),
                        coordinate("0", "0")
                ),
                1,
                0
        );
    }

    private List<BigDecimal> coordinate(String longitude, String latitude) {
        return List.of(new BigDecimal(longitude), new BigDecimal(latitude));
    }

    private OffsetDateTime offset(String value) {
        return OffsetDateTime.parse(value);
    }
}
