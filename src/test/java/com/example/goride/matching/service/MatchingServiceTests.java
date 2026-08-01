package com.example.goride.matching.service;

import com.example.goride.driver.domain.VehicleType;
import com.example.goride.matching.domain.DriverCandidate;
import com.example.goride.matching.domain.MatchingRequest;
import com.example.goride.matching.telemetry.MatchingTelemetryFailureReporter;
import com.example.goride.matching.telemetry.MatchingTelemetryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchingServiceTests {
    @Mock
    private DriverCandidateStore candidateStore;

    @Mock
    private MatchingTelemetryPort matchingTelemetry;

    @Mock
    private MatchingTelemetryFailureReporter failureReporter;

    private MatchingService matchingService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-20T03:59:30Z"), ZoneOffset.UTC);
        matchingService = new MatchingService(
                candidateStore,
                new NearestDriverStrategy(),
                matchingTelemetry,
                failureReporter,
                clock
        );
        lenient().when(candidateStore.tryLockMatchingSearch(any(), any(Duration.class)))
                .thenReturn(java.util.Optional.of("search-token"));
        lenient().when(matchingTelemetry.beginSearch(any(), any(), any(), anyInt(), any()))
                .thenAnswer(invocation -> {
                    Integer requestedAttempt = invocation.getArgument(2);
                    return OptionalInt.of(requestedAttempt == null ? 1 : requestedAttempt);
                });
        lenient().when(matchingTelemetry.recordOffer(
                any(), any(), anyInt(), anyInt(), anyDouble(), any(), any()
        )).thenReturn(true);
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
        InOrder persistenceOrder = inOrder(matchingTelemetry, candidateStore);
        persistenceOrder.verify(matchingTelemetry).recordOffer(
                eq(99L),
                eq(11L),
                eq(1),
                eq(1),
                eq(300.0),
                any(Instant.class),
                any(Instant.class)
        );
        persistenceOrder.verify(candidateStore).recordTripMatching(
                eq(99L),
                eq(11L),
                eq(1),
                any(Instant.class),
                any(Duration.class)
        );
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
        assertThat(expiresAtCaptor.getValue()).isEqualTo(Instant.parse("2026-05-20T04:00:00Z"));
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

    @Test
    void telemetryFailureReleasesRedisStateAndIsReported() {
        MatchingRequest request = request();
        DriverCandidate candidate = candidate(10L, 300);
        IllegalStateException failure = new IllegalStateException("database unavailable");
        when(candidateStore.findAvailableCandidates(request)).thenReturn(List.of(candidate));
        when(candidateStore.tryLockCandidate(eq(99L), eq(10L), any(Duration.class))).thenReturn(true);
        when(matchingTelemetry.recordOffer(
                eq(99L),
                eq(10L),
                eq(1),
                eq(1),
                eq(300.0),
                any(Instant.class),
                any(Instant.class)
        )).thenThrow(failure);

        assertThatThrownBy(() -> matchingService.findAndLockDriver(request))
                .isSameAs(failure);

        verify(candidateStore).releaseCandidateLock(10L);
        verify(candidateStore, never()).clearTripMatching(99L);
        verify(failureReporter).report("record_offer", 99L, failure);
    }

    @Test
    void redisStateFailureDoesNotReturnAnOfferAndRemainsObservable() {
        MatchingRequest request = request();
        DriverCandidate candidate = candidate(10L, 300);
        IllegalStateException failure = new IllegalStateException("redis unavailable");
        when(candidateStore.findAvailableCandidates(request)).thenReturn(List.of(candidate));
        when(candidateStore.tryLockCandidate(eq(99L), eq(10L), any(Duration.class))).thenReturn(true);
        org.mockito.Mockito.doThrow(failure).when(candidateStore).recordTripMatching(
                eq(99L),
                eq(10L),
                eq(1),
                any(Instant.class),
                any(Duration.class)
        );

        assertThatThrownBy(() -> matchingService.findAndLockDriver(request))
                .isSameAs(failure);

        verify(matchingTelemetry).recordOffer(
                eq(99L),
                eq(10L),
                eq(1),
                eq(1),
                eq(300.0),
                any(Instant.class),
                any(Instant.class)
        );
        verify(candidateStore).releaseCandidateLock(10L);
        verify(candidateStore).clearTripMatching(99L);
        verify(failureReporter).report("record_operational_state", 99L, failure);
    }

    @Test
    void concurrentSearchIsSkippedBeforeTelemetryOrCandidateLookup() {
        MatchingRequest request = request();
        when(candidateStore.tryLockMatchingSearch(eq(99L), any(Duration.class)))
                .thenReturn(java.util.Optional.empty());

        var offer = matchingService.findAndLockDriver(request);

        assertThat(offer).isEmpty();
        verify(candidateStore, never()).findAvailableCandidates(any());
        verify(matchingTelemetry, never()).beginSearch(any(), any(), any(), anyInt(), any());
        verify(candidateStore, never()).releaseMatchingSearchLock(any(), any());
    }

    @Test
    void searchLockReleaseFailureIsReportedWithoutMaskingSearchResult() {
        MatchingRequest request = request();
        IllegalStateException failure = new IllegalStateException("redis release failed");
        when(candidateStore.findAvailableCandidates(request)).thenReturn(List.of());
        org.mockito.Mockito.doThrow(failure).when(candidateStore)
                .releaseMatchingSearchLock(99L, "search-token");

        var offer = matchingService.findAndLockDriver(request);

        assertThat(offer).isEmpty();
        verify(failureReporter).report("release_search_lock", 99L, failure);
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
