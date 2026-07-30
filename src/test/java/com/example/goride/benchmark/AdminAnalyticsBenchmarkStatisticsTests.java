package com.example.goride.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAnalyticsBenchmarkStatisticsTests {
    @Test
    void loadsAllVersionedProfiles() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        var smoke = AdminAnalyticsBenchmarkProfile.load(objectMapper, "smoke");
        var medium = AdminAnalyticsBenchmarkProfile.load(objectMapper, "MEDIUM");
        var thesis = AdminAnalyticsBenchmarkProfile.load(objectMapper, "thesis");

        assertThat(smoke.trips()).isEqualTo(1_000);
        assertThat(smoke.matchingOffers()).isEqualTo(2_500);
        assertThat(medium.locationPoints()).isEqualTo(500_000);
        assertThat(thesis.users()).isEqualTo(10_000);
        assertThat(thesis.drivers()).isEqualTo(1_000);
        assertThat(thesis.trips()).isEqualTo(100_000);
        assertThat(thesis.locationPoints()).isEqualTo(2_000_000);
    }

    @Test
    void derivesContinuousPercentilesFromRawNanos() {
        var summary = AdminAnalyticsBenchmarkStatistics.summarize(
                List.of(1_000_000L, 2_000_000L, 3_000_000L, 4_000_000L, 5_000_000L),
                2
        );

        assertThat(summary.sampleCount()).isEqualTo(5);
        assertThat(summary.errorCount()).isEqualTo(2);
        assertThat(summary.minimumMs()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(summary.maximumMs()).isEqualByComparingTo("5");
        assertThat(summary.averageMs()).isEqualByComparingTo("3");
        assertThat(summary.p50Ms()).isEqualByComparingTo("3");
        assertThat(summary.p95Ms()).isEqualByComparingTo("4.8");
        assertThat(summary.standardDeviationMs()).isEqualByComparingTo("1.414214");
    }

    @Test
    void preservesErrorsWhenNoSampleSucceeds() {
        var summary = AdminAnalyticsBenchmarkStatistics.summarize(List.of(), 4);

        assertThat(summary.sampleCount()).isZero();
        assertThat(summary.errorCount()).isEqualTo(4);
        assertThat(summary.averageMs()).isNull();
        assertThat(summary.p95Ms()).isNull();
    }
}
