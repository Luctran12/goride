package com.example.goride.analytics.service;

import com.example.goride.analytics.model.AnalyticsQueryOperation;
import com.example.goride.analytics.model.AnalyticsSourceVariant;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class AnalyticsQueryObservation {
    static final String QUERY_DURATION_METRIC = "goride.analytics.query.duration";
    static final String QUERY_ERROR_METRIC = "goride.analytics.query.errors";
    static final String MATERIALIZED_FRESHNESS_METRIC =
            "goride.analytics.materialized.freshness.seconds";

    private static final Logger log = LoggerFactory.getLogger(AnalyticsQueryObservation.class);
    private static final String UNRESOLVED_SOURCE = "UNRESOLVED";

    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final AtomicReference<Instant> materializedFreshnessAt = new AtomicReference<>();

    public AnalyticsQueryObservation(MeterRegistry meterRegistry, Clock clock) {
        this.meterRegistry = meterRegistry;
        this.clock = clock;
        Gauge.builder(
                        MATERIALIZED_FRESHNESS_METRIC,
                        this,
                        AnalyticsQueryObservation::materializedFreshnessSeconds
                )
                .description("Age in seconds of the latest observed materialized analytics cutoff")
                .baseUnit("seconds")
                .register(meterRegistry);
    }

    public Scope start(
            AnalyticsQueryOperation operation,
            OffsetDateTime from,
            OffsetDateTime to,
            String timezone
    ) {
        return new Scope(operation, from, to, timezone, System.nanoTime());
    }

    public void observeMaterializedFreshness(Instant freshnessAt) {
        if (freshnessAt != null) {
            materializedFreshnessAt.set(freshnessAt);
        }
    }

    double materializedFreshnessSeconds() {
        Instant freshnessAt = materializedFreshnessAt.get();
        if (freshnessAt == null) {
            return Double.NaN;
        }
        return Math.max(0, Duration.between(freshnessAt, clock.instant()).toSeconds());
    }

    public final class Scope {
        private final AnalyticsQueryOperation operation;
        private final OffsetDateTime from;
        private final OffsetDateTime to;
        private final String timezone;
        private final long startedNanos;
        private boolean completed;

        private Scope(
                AnalyticsQueryOperation operation,
                OffsetDateTime from,
                OffsetDateTime to,
                String timezone,
                long startedNanos
        ) {
            this.operation = operation;
            this.from = from;
            this.to = to;
            this.timezone = timezone;
            this.startedNanos = startedNanos;
        }

        public void success(
                AnalyticsSourceVariant sourceVariant,
                Instant freshnessAt,
                long resultRowCount
        ) {
            finish("success", sourceVariant, freshnessAt, resultRowCount, null);
        }

        public void failure(RuntimeException exception) {
            finish("error", null, null, 0, exception);
        }

        private void finish(
                String outcome,
                AnalyticsSourceVariant sourceVariant,
                Instant freshnessAt,
                long resultRowCount,
                RuntimeException exception
        ) {
            if (completed) {
                return;
            }
            completed = true;
            String source = sourceVariant == null ? UNRESOLVED_SOURCE : sourceVariant.name();
            long durationNanos = System.nanoTime() - startedNanos;
            Timer.builder(QUERY_DURATION_METRIC)
                    .description("Admin analytics request duration")
                    .tag("queryType", operation.name())
                    .tag("sourceVariant", source)
                    .tag("outcome", outcome)
                    .register(meterRegistry)
                    .record(durationNanos, TimeUnit.NANOSECONDS);

            if (exception == null) {
                if (sourceVariant == AnalyticsSourceVariant.MATERIALIZED) {
                    observeMaterializedFreshness(freshnessAt);
                }
                log.info(
                        "Admin analytics query completed queryType={} from={} to={} timezone={} "
                                + "sourceVariant={} durationMs={} resultRowCount={} refreshCutoff={}",
                        operation,
                        from,
                        to,
                        timezone,
                        source,
                        TimeUnit.NANOSECONDS.toMillis(durationNanos),
                        resultRowCount,
                        freshnessAt
                );
                return;
            }

            Counter.builder(QUERY_ERROR_METRIC)
                    .description("Admin analytics request failures")
                    .tag("queryType", operation.name())
                    .tag("sourceVariant", source)
                    .tag("outcome", outcome)
                    .register(meterRegistry)
                    .increment();
            log.warn(
                    "Admin analytics query failed queryType={} from={} to={} timezone={} "
                            + "sourceVariant={} durationMs={} errorType={}",
                    operation,
                    from,
                    to,
                    timezone,
                    source,
                    TimeUnit.NANOSECONDS.toMillis(durationNanos),
                    exception.getClass().getSimpleName()
            );
        }
    }
}
