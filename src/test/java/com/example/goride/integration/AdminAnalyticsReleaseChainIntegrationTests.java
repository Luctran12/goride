package com.example.goride.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminAnalyticsReleaseChainIntegrationTests {
    private static final String FORECASTING_RELEASE =
            "20260808-admin-demand-forecasting";
    private static final List<String> RELEASES = List.of(
            "20260727-admin-analytics-telemetry",
            "20260729-admin-analytics-spatial-indexes",
            "20260729-admin-analytics-materialized",
            FORECASTING_RELEASE
    );
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:15-3.3")
                    .asCompatibleSubstituteFor("postgres")
    )
            .withDatabaseName("goride_release_chain")
            .withUsername("goride")
            .withPassword("goride");

    static {
        POSTGRES.start();
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void releaseDatabaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.autoconfigure.exclude", () -> "");
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.data.redis.repositories.enabled", () -> "false");
        registry.add("app.matching.timeout-scheduler.enabled", () -> "false");
        registry.add("app.driver.availability.scheduler.enabled", () -> "false");
        registry.add("app.analytics.telemetry.supply-snapshot-enabled", () -> "false");
        registry.add("app.analytics.direct-query-enabled", () -> "true");
        registry.add("app.analytics.materialized.enabled", () -> "false");
        registry.add("app.notifications.fcm.enabled", () -> "false");
    }

    @Test
    void appliesVerifiesAndRollsBackTheCompleteReleaseChain() throws IOException {
        rollbackAll();
        try {
            applyAndVerifyAll();
            assertReleaseObjectsExist();

            rollbackAll();
            assertReleaseObjectsAreAbsent();

            applyAndVerifyAll();
            assertReleaseObjectsExist();
        }
        finally {
            rollbackAll();
        }
    }

    private void applyAndVerifyAll() throws IOException {
        for (String release : RELEASES) {
            execute(release, "precheck.sql");
            execute(release, "apply.sql");
            execute(release, "verify.sql");
        }
        execute(FORECASTING_RELEASE, "integration-test.sql");
    }

    private void assertReleaseObjectsExist() {
        assertThat(regclass("public.matching_runs")).isNotNull();
        assertThat(regclass("public.matching_offer_events")).isNotNull();
        assertThat(regclass("public.driver_supply_snapshots")).isNotNull();
        assertThat(regclass("public.idx_trips_analytics_requested_at")).isNotNull();
        assertThat(regclass("public.idx_trips_pickup_location_gist")).isNotNull();
        assertThat(regclass("analytics.materialized_refresh_state")).isNotNull();
        assertThat(regclass("analytics.mv_trip_daily")).isNotNull();
        assertThat(regclass("analytics.mv_demand_hourly_cell")).isNotNull();
        assertThat(regclass("analytics.mv_supply_hourly")).isNotNull();
        assertThat(regclass("analytics.mv_matching_daily")).isNotNull();
        assertThat(regclass("analytics.processing_runs")).isNotNull();
        assertThat(regclass("analytics.data_quality_results")).isNotNull();
        assertThat(regclass("analytics.demand_features")).isNotNull();
        assertThat(regclass("analytics.model_versions")).isNotNull();
        assertThat(regclass("analytics.forecast_runs")).isNotNull();
        assertThat(regclass("analytics.demand_forecasts")).isNotNull();
        assertThat(regclass("analytics.forecast_evaluations")).isNotNull();
    }

    private void assertReleaseObjectsAreAbsent() {
        assertThat(regclass("analytics.forecast_evaluations")).isNull();
        assertThat(regclass("analytics.demand_forecasts")).isNull();
        assertThat(regclass("analytics.forecast_runs")).isNull();
        assertThat(regclass("analytics.model_versions")).isNull();
        assertThat(regclass("analytics.demand_features")).isNull();
        assertThat(regclass("analytics.data_quality_results")).isNull();
        assertThat(regclass("analytics.processing_runs")).isNull();
        assertThat(regclass("analytics.mv_matching_daily")).isNull();
        assertThat(regclass("analytics.materialized_refresh_state")).isNull();
        assertThat(regclass("public.idx_trips_analytics_requested_at")).isNull();
        assertThat(regclass("public.idx_trips_pickup_location_gist")).isNull();
        assertThat(regclass("public.matching_offer_events")).isNull();
        assertThat(regclass("public.driver_supply_snapshots")).isNull();
        assertThat(regclass("public.matching_runs")).isNull();
    }

    private void rollbackAll() throws IOException {
        for (int index = RELEASES.size() - 1; index >= 0; index--) {
            execute(RELEASES.get(index), "rollback.sql");
        }
    }

    private void execute(String release, String file) throws IOException {
        Path path = Path.of("db", "releases", release, file);
        jdbcTemplate.execute(Files.readString(path));
    }

    private String regclass(String name) {
        return jdbcTemplate.queryForObject(
                "SELECT to_regclass(?)::TEXT",
                String.class,
                name
        );
    }
}
