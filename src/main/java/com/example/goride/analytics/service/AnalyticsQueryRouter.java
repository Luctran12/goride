package com.example.goride.analytics.service;

import com.example.goride.analytics.config.AnalyticsMaterializedProperties;
import com.example.goride.analytics.model.AnalyticsBucket;
import com.example.goride.analytics.model.AnalyticsFilter;
import com.example.goride.analytics.model.AnalyticsQueryOperation;
import com.example.goride.analytics.model.AnalyticsSourceVariant;
import com.example.goride.analytics.repository.DirectAnalyticsQueryPort;
import com.example.goride.analytics.repository.MaterializedAnalyticsQueryPort;
import com.example.goride.analytics.repository.MaterializedAnalyticsQueryPort.Snapshot;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class AnalyticsQueryRouter {
    private final DirectAnalyticsQueryPort directQueryPort;
    private final Optional<MaterializedAnalyticsQueryPort> materializedQueryPort;
    private final AnalyticsMaterializedProperties properties;
    private final Counter fallbackCounter;

    public AnalyticsQueryRouter(
            List<DirectAnalyticsQueryPort> queryPorts,
            AnalyticsMaterializedProperties properties,
            MeterRegistry meterRegistry
    ) {
        this.directQueryPort = queryPorts.stream()
                .filter(port -> !(port instanceof MaterializedAnalyticsQueryPort))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Direct analytics query port is not configured"
                ));
        this.materializedQueryPort = queryPorts.stream()
                .filter(MaterializedAnalyticsQueryPort.class::isInstance)
                .map(MaterializedAnalyticsQueryPort.class::cast)
                .findFirst();
        this.properties = properties;
        this.fallbackCounter = Counter.builder("goride.analytics.materialized.fallback")
                .description("Materialized analytics queries that safely fell back to direct SQL")
                .register(meterRegistry);
    }

    public Selection select(
            AnalyticsFilter filter,
            AnalyticsQueryOperation operation,
            AnalyticsBucket bucket,
            Instant directFreshnessAt
    ) {
        return select(filter, operation, bucket, directFreshnessAt, true);
    }

    public Selection select(
            AnalyticsFilter filter,
            AnalyticsQueryOperation operation,
            AnalyticsBucket bucket,
            Instant directFreshnessAt,
            boolean materializedEligible
    ) {
        if (properties.getQueryVariant() == AnalyticsSourceVariant.DIRECT) {
            return direct(directFreshnessAt);
        }
        Optional<Selection> materialized = materializedEligible
                ? materializedSelection(filter, operation, bucket)
                : Optional.empty();
        if (materialized.isPresent()) {
            return materialized.get();
        }
        if (properties.isFallbackEnabled()) {
            fallbackCounter.increment();
            return direct(directFreshnessAt);
        }
        throw new BusinessException(
                ErrorCode.ANALYTICS_DATA_UNAVAILABLE,
                "Materialized analytics are unavailable for this query",
                Map.of("operation", operation.name())
        );
    }

    private Optional<Selection> materializedSelection(
            AnalyticsFilter filter,
            AnalyticsQueryOperation operation,
            AnalyticsBucket bucket
    ) {
        if (!properties.isEnabled() || materializedQueryPort.isEmpty()) {
            return Optional.empty();
        }
        Optional<Snapshot> snapshot = materializedQueryPort.orElseThrow().currentSnapshot();
        if (snapshot.isEmpty() || !metadataMatches(snapshot.get())) {
            return Optional.empty();
        }
        if (!properties.getReportingTimezone().equals(filter.reportingTimezone().getId())) {
            return Optional.empty();
        }
        boolean supported = switch (operation) {
            case OVERVIEW, MATCHING_PERFORMANCE, MATCHING_FUNNEL ->
                    isLocalDayBoundary(filter.from().atZone(filter.reportingTimezone()))
                            && isLocalDayBoundary(filter.to().atZone(filter.reportingTimezone()));
            case DEMAND_TIMESERIES, SUPPLY_TIMESERIES, DEMAND_HEATMAP ->
                    (bucket != null || operation == AnalyticsQueryOperation.DEMAND_HEATMAP)
                            && isLocalHourBoundary(filter.from().atZone(filter.reportingTimezone()))
                            && isLocalHourBoundary(filter.to().atZone(filter.reportingTimezone()));
        };
        if (!supported) {
            return Optional.empty();
        }
        return Optional.of(new Selection(
                materializedQueryPort.orElseThrow(),
                AnalyticsSourceVariant.MATERIALIZED,
                snapshot.get().completedAt()
        ));
    }

    private boolean metadataMatches(Snapshot snapshot) {
        return properties.getReportingTimezone().equals(snapshot.reportingTimezone())
                && properties.getProjectedSrid() == snapshot.projectedSrid()
                && properties.getBaseCellSizeMeters() == snapshot.baseCellSizeMeters();
    }

    private boolean isLocalDayBoundary(ZonedDateTime value) {
        return value.equals(value.truncatedTo(ChronoUnit.DAYS));
    }

    private boolean isLocalHourBoundary(ZonedDateTime value) {
        return value.equals(value.truncatedTo(ChronoUnit.HOURS));
    }

    private Selection direct(Instant freshnessAt) {
        return new Selection(
                directQueryPort,
                AnalyticsSourceVariant.DIRECT,
                freshnessAt
        );
    }

    public record Selection(
            DirectAnalyticsQueryPort queryPort,
            AnalyticsSourceVariant sourceVariant,
            Instant freshnessAt
    ) {
    }
}
