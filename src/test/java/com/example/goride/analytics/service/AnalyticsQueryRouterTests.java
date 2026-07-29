package com.example.goride.analytics.service;

import com.example.goride.analytics.config.AnalyticsMaterializedProperties;
import com.example.goride.analytics.model.AnalyticsFilter;
import com.example.goride.analytics.model.AnalyticsQueryOperation;
import com.example.goride.analytics.model.AnalyticsSourceVariant;
import com.example.goride.analytics.repository.DirectAnalyticsQueryPort;
import com.example.goride.analytics.repository.MaterializedAnalyticsQueryPort;
import com.example.goride.analytics.repository.MaterializedAnalyticsQueryPort.Snapshot;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyticsQueryRouterTests {
    private static final Instant DIRECT_FRESHNESS =
            Instant.parse("2026-07-29T08:00:00Z");
    private static final Instant MATERIALIZED_FRESHNESS =
            Instant.parse("2026-07-29T07:55:00Z");

    @Test
    void directRemainsDefaultWithoutReadingMaterializedState() {
        DirectAnalyticsQueryPort direct = mock(DirectAnalyticsQueryPort.class);
        MaterializedAnalyticsQueryPort materialized =
                mock(MaterializedAnalyticsQueryPort.class);
        AnalyticsQueryRouter router = router(
                direct,
                materialized,
                new AnalyticsMaterializedProperties()
        );

        var selection = router.select(
                dayFilter(),
                AnalyticsQueryOperation.OVERVIEW,
                null,
                DIRECT_FRESHNESS
        );

        assertThat(selection.queryPort()).isSameAs(direct);
        assertThat(selection.sourceVariant()).isEqualTo(AnalyticsSourceVariant.DIRECT);
        assertThat(selection.freshnessAt()).isEqualTo(DIRECT_FRESHNESS);
    }

    @Test
    void selectsCompatibleSuccessfulMaterializedSnapshot() {
        DirectAnalyticsQueryPort direct = mock(DirectAnalyticsQueryPort.class);
        MaterializedAnalyticsQueryPort materialized =
                mock(MaterializedAnalyticsQueryPort.class);
        AnalyticsMaterializedProperties properties = materializedProperties(true);
        when(materialized.currentSnapshot()).thenReturn(Optional.of(snapshot()));
        AnalyticsQueryRouter router = router(direct, materialized, properties);

        var selection = router.select(
                dayFilter(),
                AnalyticsQueryOperation.OVERVIEW,
                null,
                DIRECT_FRESHNESS
        );

        assertThat(selection.queryPort()).isSameAs(materialized);
        assertThat(selection.sourceVariant())
                .isEqualTo(AnalyticsSourceVariant.MATERIALIZED);
        assertThat(selection.freshnessAt()).isEqualTo(MATERIALIZED_FRESHNESS);
    }

    @Test
    void safelyFallsBackForUnalignedOrExplicitlyIneligibleQueries() {
        DirectAnalyticsQueryPort direct = mock(DirectAnalyticsQueryPort.class);
        MaterializedAnalyticsQueryPort materialized =
                mock(MaterializedAnalyticsQueryPort.class);
        AnalyticsMaterializedProperties properties = materializedProperties(true);
        when(materialized.currentSnapshot()).thenReturn(Optional.of(snapshot()));
        AnalyticsQueryRouter router = router(direct, materialized, properties);
        AnalyticsFilter unaligned = new AnalyticsFilter(
                Instant.parse("2026-06-30T17:30:00Z"),
                Instant.parse("2026-07-01T17:00:00Z"),
                ZoneId.of("Asia/Ho_Chi_Minh"),
                null,
                null
        );

        assertThat(router.select(
                unaligned,
                AnalyticsQueryOperation.OVERVIEW,
                null,
                DIRECT_FRESHNESS
        ).sourceVariant()).isEqualTo(AnalyticsSourceVariant.DIRECT);
        assertThat(router.select(
                dayFilter(),
                AnalyticsQueryOperation.DEMAND_HEATMAP,
                null,
                DIRECT_FRESHNESS,
                false
        ).sourceVariant()).isEqualTo(AnalyticsSourceVariant.DIRECT);
    }

    @Test
    void reportsUnavailableWhenFallbackIsDisabled() {
        DirectAnalyticsQueryPort direct = mock(DirectAnalyticsQueryPort.class);
        MaterializedAnalyticsQueryPort materialized =
                mock(MaterializedAnalyticsQueryPort.class);
        AnalyticsMaterializedProperties properties = materializedProperties(false);
        when(materialized.currentSnapshot()).thenReturn(Optional.empty());
        AnalyticsQueryRouter router = router(direct, materialized, properties);

        assertThatThrownBy(() -> router.select(
                dayFilter(),
                AnalyticsQueryOperation.OVERVIEW,
                null,
                DIRECT_FRESHNESS
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.errorCode())
                        .isEqualTo(ErrorCode.ANALYTICS_DATA_UNAVAILABLE));
    }

    private AnalyticsQueryRouter router(
            DirectAnalyticsQueryPort direct,
            MaterializedAnalyticsQueryPort materialized,
            AnalyticsMaterializedProperties properties
    ) {
        return new AnalyticsQueryRouter(
                List.of(direct, materialized),
                properties,
                new SimpleMeterRegistry()
        );
    }

    private AnalyticsMaterializedProperties materializedProperties(boolean fallback) {
        AnalyticsMaterializedProperties properties = new AnalyticsMaterializedProperties();
        properties.setEnabled(true);
        properties.setQueryVariant(AnalyticsSourceVariant.MATERIALIZED);
        properties.setFallbackEnabled(fallback);
        return properties;
    }

    private Snapshot snapshot() {
        return new Snapshot(
                MATERIALIZED_FRESHNESS,
                "Asia/Ho_Chi_Minh",
                32648,
                250
        );
    }

    private AnalyticsFilter dayFilter() {
        return new AnalyticsFilter(
                Instant.parse("2026-06-30T17:00:00Z"),
                Instant.parse("2026-07-01T17:00:00Z"),
                ZoneId.of("Asia/Ho_Chi_Minh"),
                null,
                null
        );
    }
}
