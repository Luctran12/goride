package com.example.goride.analytics.service;

import com.example.goride.analytics.model.AnalyticsQueryOperation;
import com.example.goride.analytics.model.AnalyticsSourceVariant;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsQueryObservationTests {
    private static final Instant NOW = Instant.parse("2026-07-30T08:00:00Z");

    private SimpleMeterRegistry meterRegistry;
    private AnalyticsQueryObservation observation;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        observation = new AnalyticsQueryObservation(
                meterRegistry,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void recordsBoundedQueryTagsAndMaterializedFreshness() {
        AnalyticsQueryObservation.Scope scope = observation.start(
                AnalyticsQueryOperation.DEMAND_TIMESERIES,
                OffsetDateTime.parse("2026-07-01T00:00:00+07:00"),
                OffsetDateTime.parse("2026-07-02T00:00:00+07:00"),
                "Asia/Ho_Chi_Minh"
        );

        scope.success(
                AnalyticsSourceVariant.MATERIALIZED,
                NOW.minusSeconds(90),
                24
        );
        scope.success(
                AnalyticsSourceVariant.MATERIALIZED,
                NOW.minusSeconds(30),
                24
        );

        assertThat(meterRegistry.find(AnalyticsQueryObservation.QUERY_DURATION_METRIC)
                .tags(
                        "queryType", "DEMAND_TIMESERIES",
                        "sourceVariant", "MATERIALIZED",
                        "outcome", "success"
                )
                .timer()
                .count()).isEqualTo(1);
        assertThat(meterRegistry.get(AnalyticsQueryObservation.MATERIALIZED_FRESHNESS_METRIC)
                .gauge()
                .value()).isEqualTo(90);
    }

    @Test
    void recordsFailuresWithoutHighCardinalityExceptionTags() {
        AnalyticsQueryObservation.Scope scope = observation.start(
                AnalyticsQueryOperation.OVERVIEW,
                OffsetDateTime.parse("2026-07-01T00:00:00Z"),
                OffsetDateTime.parse("2026-07-02T00:00:00Z"),
                "UTC"
        );

        scope.failure(new IllegalStateException("database unavailable"));

        assertThat(meterRegistry.find(AnalyticsQueryObservation.QUERY_DURATION_METRIC)
                .tags(
                        "queryType", "OVERVIEW",
                        "sourceVariant", "UNRESOLVED",
                        "outcome", "error"
                )
                .timer()
                .count()).isEqualTo(1);
        assertThat(meterRegistry.find(AnalyticsQueryObservation.QUERY_ERROR_METRIC)
                .tags(
                        "queryType", "OVERVIEW",
                        "sourceVariant", "UNRESOLVED",
                        "outcome", "error"
                )
                .counter()
                .count()).isEqualTo(1);
        assertThat(meterRegistry.get(AnalyticsQueryObservation.QUERY_ERROR_METRIC)
                .counter()
                .getId()
                .getTags()).extracting(tag -> tag.getKey())
                .containsExactlyInAnyOrder("queryType", "sourceVariant", "outcome");
    }
}
