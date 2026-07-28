package com.example.goride.matching.service;

import com.example.goride.matching.telemetry.ExpiredMatchingOffer;
import com.example.goride.matching.telemetry.MatchingTelemetryPort;
import com.example.goride.matching.telemetry.MatchingTelemetryFailureReporter;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchingOfferTimeoutSchedulerTests {
    @Test
    void scansActiveMatchingTrips() {
        DriverCandidateStore candidateStore = mock(DriverCandidateStore.class);
        MatchingOfferTimeoutService timeoutService = mock(MatchingOfferTimeoutService.class);
        MatchingTelemetryPort matchingTelemetry = mock(MatchingTelemetryPort.class);
        MatchingTelemetryFailureReporter failureReporter =
                mock(MatchingTelemetryFailureReporter.class);
        Clock clock = Clock.fixed(Instant.parse("2026-05-20T04:00:00Z"), ZoneOffset.UTC);
        when(candidateStore.findActiveMatchingTripIds()).thenReturn(Set.of(99L, 100L));
        ExpiredMatchingOffer recovered = new ExpiredMatchingOffer(
                101L,
                20L,
                2,
                Instant.parse("2026-05-20T03:59:00Z"),
                Set.of(19L)
        );
        when(matchingTelemetry.findExpiredOffers(clock.instant(), 100)).thenReturn(List.of(recovered));
        MatchingOfferTimeoutScheduler scheduler = new MatchingOfferTimeoutScheduler(
                candidateStore,
                timeoutService,
                matchingTelemetry,
                failureReporter,
                clock
        );

        scheduler.scanExpiredOffers();

        verify(timeoutService).processExpiredOffer(99L);
        verify(timeoutService).processExpiredOffer(100L);
        verify(timeoutService).processRecoveredExpiredOffer(recovered);
    }
}
