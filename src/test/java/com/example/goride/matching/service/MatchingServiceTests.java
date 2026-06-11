package com.example.goride.matching.service;

import com.example.goride.driver.domain.VehicleType;
import com.example.goride.matching.domain.DriverCandidate;
import com.example.goride.matching.domain.MatchingRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchingServiceTests {
    @Mock
    private DriverCandidateStore candidateStore;

    private MatchingService matchingService;

    @BeforeEach
    void setUp() {
        matchingService = new MatchingService(candidateStore, new NearestDriverStrategy());
    }

    @Test
    void locksFirstAvailableRankedCandidateAndRecordsMatchingState() {
        MatchingRequest request = request();
        DriverCandidate far = candidate(10L, 900);
        DriverCandidate near = candidate(11L, 300);
        when(candidateStore.findAvailableCandidates(request)).thenReturn(List.of(far, near));
        when(candidateStore.tryLockCandidate(eq(99L), eq(11L), any(Duration.class))).thenReturn(true);

        var offer = matchingService.findAndLockDriver(request);

        ArgumentCaptor<Instant> expiresAtCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(candidateStore).tryLockCandidate(eq(99L), eq(11L), any(Duration.class));
        verify(candidateStore).recordTripMatching(
                eq(99L),
                eq(11L),
                eq(1),
                expiresAtCaptor.capture(),
                any(Duration.class)
        );
        assertThat(offer).isPresent();
        assertThat(offer.get().candidate().driverId()).isEqualTo(11L);
        assertThat(offer.get().attempt()).isEqualTo(1);
        assertThat(expiresAtCaptor.getValue()).isAfter(Instant.now().minusSeconds(1));
    }

    @Test
    void skipsLockedCandidateAndKeepsFirstOfferAttempt() {
        MatchingRequest request = request();
        DriverCandidate first = candidate(10L, 300);
        DriverCandidate second = candidate(11L, 500);
        when(candidateStore.findAvailableCandidates(request)).thenReturn(List.of(first, second));
        when(candidateStore.tryLockCandidate(eq(99L), eq(10L), any(Duration.class))).thenReturn(false);
        when(candidateStore.tryLockCandidate(eq(99L), eq(11L), any(Duration.class))).thenReturn(true);

        var offer = matchingService.findAndLockDriver(request);

        verify(candidateStore).recordTripMatching(eq(99L), eq(11L), eq(1), any(Instant.class), any(Duration.class));
        assertThat(offer).isPresent();
        assertThat(offer.get().candidate().driverId()).isEqualTo(11L);
        assertThat(offer.get().attempt()).isEqualTo(1);
    }

    @Test
    void returnsEmptyWhenNoDriverCanBeLocked() {
        MatchingRequest request = request();
        DriverCandidate candidate = candidate(10L, 300);
        when(candidateStore.findAvailableCandidates(request)).thenReturn(List.of(candidate));
        when(candidateStore.tryLockCandidate(eq(99L), eq(10L), any(Duration.class))).thenReturn(false);

        var offer = matchingService.findAndLockDriver(request);

        verify(candidateStore, never()).recordTripMatching(any(), any(), anyInt(), any(), any());
        assertThat(offer).isEmpty();
    }

    @Test
    void retryExcludesRejectedDriversAndRecordsAttemptState() {
        MatchingRequest request = request();
        DriverCandidate rejected = candidate(10L, 300);
        DriverCandidate next = candidate(11L, 500);
        when(candidateStore.findAvailableCandidates(request)).thenReturn(List.of(rejected, next));
        when(candidateStore.tryLockCandidate(eq(99L), eq(11L), any(Duration.class))).thenReturn(true);

        var offer = matchingService.findAndLockDriver(request, 2, Set.of(10L));

        verify(candidateStore, never()).tryLockCandidate(eq(99L), eq(10L), any(Duration.class));
        verify(candidateStore).recordTripMatching(eq(99L), eq(11L), eq(2), any(Instant.class), eq(Set.of(10L)), any(Duration.class));
        assertThat(offer).isPresent();
        assertThat(offer.get().candidate().driverId()).isEqualTo(11L);
        assertThat(offer.get().attempt()).isEqualTo(2);
    }

    private MatchingRequest request() {
        return new MatchingRequest(
                99L,
                VehicleType.MOTORBIKE,
                BigDecimal.valueOf(10.7769),
                BigDecimal.valueOf(106.7009),
                BigDecimal.valueOf(5),
                3
        );
    }

    private DriverCandidate candidate(Long driverId, long distanceMeters) {
        return new DriverCandidate(
                driverId,
                distanceMeters,
                VehicleType.MOTORBIKE,
                BigDecimal.valueOf(5.0),
                "Driver " + driverId,
                null
        );
    }
}
