package com.example.goride.matching.service;

import com.example.goride.matching.telemetry.MatchingTelemetryPort;
import com.example.goride.matching.telemetry.MatchingTelemetryFailureReporter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
@ConditionalOnProperty(
        prefix = "app.matching.timeout-scheduler",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class MatchingOfferTimeoutScheduler {
    private final DriverCandidateStore candidateStore;
    private final MatchingOfferTimeoutService timeoutService;
    private final MatchingTelemetryPort matchingTelemetry;
    private final MatchingTelemetryFailureReporter matchingTelemetryFailureReporter;
    private final Clock clock;

    public MatchingOfferTimeoutScheduler(
            DriverCandidateStore candidateStore,
            MatchingOfferTimeoutService timeoutService,
            MatchingTelemetryPort matchingTelemetry,
            MatchingTelemetryFailureReporter matchingTelemetryFailureReporter,
            Clock clock
    ) {
        this.candidateStore = candidateStore;
        this.timeoutService = timeoutService;
        this.matchingTelemetry = matchingTelemetry;
        this.matchingTelemetryFailureReporter = matchingTelemetryFailureReporter;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${app.matching.timeout-scheduler.fixed-delay-ms:5000}",
            initialDelayString = "${app.matching.timeout-scheduler.initial-delay-ms:10000}"
    )
    public void scanExpiredOffers() {
        try {
            candidateStore.findActiveMatchingTripIds().forEach(timeoutService::processExpiredOffer);
            matchingTelemetry.findExpiredOffers(clock.instant(), 100)
                    .forEach(timeoutService::processRecoveredExpiredOffer);
        } catch (RuntimeException exception) {
            matchingTelemetryFailureReporter.report("recovery_scan", null, exception);
            throw exception;
        }
    }
}
