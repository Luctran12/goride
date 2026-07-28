package com.example.goride.analytics.service;

import com.example.goride.analytics.config.AnalyticsTelemetryProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "app.analytics.telemetry",
        name = "supply-snapshot-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class DriverSupplySnapshotScheduler {
    private static final Logger log = LoggerFactory.getLogger(DriverSupplySnapshotScheduler.class);

    private final DriverSupplySnapshotService snapshotService;
    private final AnalyticsTelemetryProperties properties;
    private final MeterRegistry meterRegistry;

    public DriverSupplySnapshotScheduler(
            DriverSupplySnapshotService snapshotService,
            AnalyticsTelemetryProperties properties,
            MeterRegistry meterRegistry
    ) {
        this.snapshotService = snapshotService;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(
            fixedDelayString = "${app.analytics.telemetry.supply-snapshot-fixed-delay-ms:300000}",
            initialDelayString = "${app.analytics.telemetry.supply-snapshot-initial-delay-ms:30000}"
    )
    public void captureSnapshot() {
        if (!properties.isSupplySnapshotEnabled()) {
            return;
        }
        try {
            snapshotService.captureCurrentSupply();
        } catch (RuntimeException exception) {
            meterRegistry.counter(
                    "goride.analytics.supply.snapshot.failures",
                    "exception",
                    exception.getClass().getSimpleName()
            ).increment();
            log.error("Driver-supply snapshot failed", exception);
            throw exception;
        }
    }
}
