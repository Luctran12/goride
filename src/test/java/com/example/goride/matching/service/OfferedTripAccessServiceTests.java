package com.example.goride.matching.service;

import com.example.goride.matching.domain.TripMatchingState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfferedTripAccessServiceTests {
    private static final Instant NOW = Instant.parse("2026-07-22T14:30:00Z");

    @Mock
    private DriverCandidateStore candidateStore;

    private OfferedTripAccessService service;

    @BeforeEach
    void setUp() {
        service = new OfferedTripAccessService(candidateStore, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void allowsDriverHoldingActiveOffer() {
        when(candidateStore.findTripMatching(99L)).thenReturn(Optional.of(state(20L, NOW.plusSeconds(30))));

        assertThat(service.hasActiveOffer(99L, 20L)).isTrue();
    }

    @Test
    void rejectsWrongDriver() {
        when(candidateStore.findTripMatching(99L)).thenReturn(Optional.of(state(20L, NOW.plusSeconds(30))));

        assertThat(service.hasActiveOffer(99L, 21L)).isFalse();
    }

    @Test
    void rejectsExpiredOffer() {
        when(candidateStore.findTripMatching(99L)).thenReturn(Optional.of(state(20L, NOW.minusSeconds(1))));

        assertThat(service.hasActiveOffer(99L, 20L)).isFalse();
    }

    @Test
    void rejectsMissingOffer() {
        when(candidateStore.findTripMatching(99L)).thenReturn(Optional.empty());

        assertThat(service.hasActiveOffer(99L, 20L)).isFalse();
    }

    private TripMatchingState state(Long offeredDriverId, Instant expiresAt) {
        return new TripMatchingState(99L, offeredDriverId, 1, expiresAt, Set.of());
    }
}