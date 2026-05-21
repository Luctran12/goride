package com.example.goride.matching.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

    public MatchingOfferTimeoutScheduler(
            DriverCandidateStore candidateStore,
            MatchingOfferTimeoutService timeoutService
    ) {
        this.candidateStore = candidateStore;
        this.timeoutService = timeoutService;
    }

    @Scheduled(
            fixedDelayString = "${app.matching.timeout-scheduler.fixed-delay-ms:5000}",
            initialDelayString = "${app.matching.timeout-scheduler.initial-delay-ms:10000}"
    )
    public void scanExpiredOffers() {
        candidateStore.findActiveMatchingTripIds().forEach(timeoutService::processExpiredOffer);
    }
}
