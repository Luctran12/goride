package com.example.goride.analytics.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(
        name = "app.analytics.materialized.enabled",
        havingValue = "true"
)
public class MaterializedAnalyticsRefreshService {
    private static final Logger log =
            LoggerFactory.getLogger(MaterializedAnalyticsRefreshService.class);
    private static final String LOCK_NAME = "goride-admin-analytics-materialized-refresh";
    private static final List<String> MATERIALIZED_VIEWS = List.of(
            "analytics.mv_trip_daily",
            "analytics.mv_demand_hourly_cell",
            "analytics.mv_supply_hourly",
            "analytics.mv_matching_daily"
    );

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate refreshTransaction;
    private final TransactionTemplate failureTransaction;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Counter skippedCounter;
    private final Timer durationTimer;

    public MaterializedAnalyticsRefreshService(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            MeterRegistry meterRegistry
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.refreshTransaction = new TransactionTemplate(transactionManager);
        this.refreshTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
        this.refreshTransaction.setIsolationLevel(
                TransactionDefinition.ISOLATION_REPEATABLE_READ
        );
        this.refreshTransaction.setTimeout(300);
        this.failureTransaction = new TransactionTemplate(transactionManager);
        this.failureTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
        this.failureTransaction.setTimeout(15);
        this.successCounter = Counter.builder("goride.analytics.materialized.refresh")
                .tag("outcome", "success")
                .register(meterRegistry);
        this.failureCounter = Counter.builder("goride.analytics.materialized.refresh")
                .tag("outcome", "failure")
                .register(meterRegistry);
        this.skippedCounter = Counter.builder("goride.analytics.materialized.refresh")
                .tag("outcome", "skipped")
                .register(meterRegistry);
        this.durationTimer = Timer.builder("goride.analytics.materialized.refresh.duration")
                .description("Materialized analytics refresh duration")
                .register(meterRegistry);
    }

    public RefreshResult refresh() {
        long startedNanos = System.nanoTime();
        try {
            RefreshResult result = refreshTransaction.execute(status -> refreshInTransaction(
                    startedNanos
            ));
            if (result == null) {
                throw new IllegalStateException("Materialized refresh returned no result");
            }
            recordDuration(startedNanos);
            if (result.refreshed()) {
                successCounter.increment();
                log.info(
                        "Materialized analytics refresh completed cutoff={} concurrent={} durationMs={}",
                        result.cutoff(),
                        result.concurrent(),
                        result.durationMs()
                );
            } else {
                skippedCounter.increment();
                log.info("Materialized analytics refresh skipped because another node owns the lock");
            }
            return result;
        } catch (RuntimeException exception) {
            long durationMs = elapsedMillis(startedNanos);
            failureCounter.increment();
            recordDuration(startedNanos);
            recordFailure(durationMs, exception);
            log.error(
                    "Materialized analytics refresh failed durationMs={}",
                    durationMs,
                    exception
            );
            throw exception;
        }
    }

    private RefreshResult refreshInTransaction(long startedNanos) {
        Boolean locked = jdbcTemplate.queryForObject(
                "SELECT pg_try_advisory_xact_lock(hashtext(?))",
                Boolean.class,
                LOCK_NAME
        );
        if (!Boolean.TRUE.equals(locked)) {
            return RefreshResult.skipped();
        }
        OffsetDateTime cutoffValue = jdbcTemplate.queryForObject(
                "SELECT transaction_timestamp()",
                OffsetDateTime.class
        );
        Instant cutoff = cutoffValue == null ? null : cutoffValue.toInstant();
        if (cutoff == null) {
            throw new IllegalStateException("PostgreSQL refresh cutoff is unavailable");
        }
        jdbcTemplate.update(
                """
                        UPDATE analytics.materialized_refresh_state
                        SET status = 'RUNNING',
                            last_started_at = ?,
                            last_error = NULL
                        WHERE id = 1
                """,
                cutoffValue
        );
        boolean concurrent = allViewsPopulated();
        for (String view : MATERIALIZED_VIEWS) {
            jdbcTemplate.execute(
                    "REFRESH MATERIALIZED VIEW "
                            + (concurrent ? "CONCURRENTLY " : "")
                            + view
            );
        }
        long durationMs = elapsedMillis(startedNanos);
        jdbcTemplate.update(
                """
                        UPDATE analytics.materialized_refresh_state
                        SET status = 'SUCCESS',
                            last_completed_at = ?,
                            last_duration_ms = ?,
                            last_error = NULL
                        WHERE id = 1
                """,
                cutoffValue,
                durationMs
        );
        return new RefreshResult(true, concurrent, cutoff, durationMs);
    }

    private boolean allViewsPopulated() {
        Integer populated = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM pg_matviews
                        WHERE schemaname = 'analytics'
                          AND matviewname IN (
                              'mv_trip_daily',
                              'mv_demand_hourly_cell',
                              'mv_supply_hourly',
                              'mv_matching_daily'
                          )
                          AND ispopulated
                        """,
                Integer.class
        );
        return populated != null && populated == MATERIALIZED_VIEWS.size();
    }

    private void recordFailure(long durationMs, RuntimeException exception) {
        String error = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        String boundedError = error.length() <= 1000 ? error : error.substring(0, 1000);
        try {
            failureTransaction.executeWithoutResult(status -> jdbcTemplate.update(
                    """
                            UPDATE analytics.materialized_refresh_state
                            SET status = 'FAILED',
                                last_duration_ms = ?,
                                last_error = ?
                            WHERE id = 1
                            """,
                    durationMs,
                    boundedError
            ));
        } catch (RuntimeException stateFailure) {
            log.error(
                    "Could not persist materialized analytics refresh failure state",
                    stateFailure
            );
        }
    }

    private void recordDuration(long startedNanos) {
        durationTimer.record(System.nanoTime() - startedNanos, TimeUnit.NANOSECONDS);
    }

    private long elapsedMillis(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }

    public record RefreshResult(
            boolean refreshed,
            boolean concurrent,
            Instant cutoff,
            long durationMs
    ) {
        public static RefreshResult skipped() {
            return new RefreshResult(false, false, null, 0);
        }
    }
}
