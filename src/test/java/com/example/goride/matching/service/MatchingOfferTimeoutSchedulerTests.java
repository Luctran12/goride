package com.example.goride.matching.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchingOfferTimeoutSchedulerTests {
    @Test
    void scansActiveMatchingTrips() {
        DriverCandidateStore candidateStore = mock(DriverCandidateStore.class);
        MatchingOfferTimeoutService timeoutService = mock(MatchingOfferTimeoutService.class);
        when(candidateStore.findActiveMatchingTripIds()).thenReturn(Set.of(99L, 100L));
        MatchingOfferTimeoutScheduler scheduler = new MatchingOfferTimeoutScheduler(candidateStore, timeoutService);

        scheduler.scanExpiredOffers();

        verify(timeoutService).processExpiredOffer(99L);
        verify(timeoutService).processExpiredOffer(100L);
    }
}
