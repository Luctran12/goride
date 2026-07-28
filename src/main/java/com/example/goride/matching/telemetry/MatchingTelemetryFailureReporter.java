package com.example.goride.matching.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MatchingTelemetryFailureReporter {
    private static final Logger log = LoggerFactory.getLogger(MatchingTelemetryFailureReporter.class);
    private static final String METRIC_NAME = "goride.matching.telemetry.failures";

    private final MeterRegistry meterRegistry;

    public MatchingTelemetryFailureReporter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void report(String stage, Long tripId, RuntimeException exception) {
        meterRegistry.counter(METRIC_NAME, "stage", stage).increment();
        log.error(
                "Matching telemetry write failed stage={} tripId={} exceptionType={} message={}",
                stage,
                tripId,
                exception.getClass().getSimpleName(),
                exception.getMessage(),
                exception
        );
    }

}
