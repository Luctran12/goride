package com.example.goride.tracking.service;

import com.example.goride.driver.service.availability.DriverAvailabilityStore;
import com.example.goride.tracking.dto.DriverLocationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripDriverLocationBootstrapServiceTests {
    @Mock
    private DriverAvailabilityStore driverAvailabilityStore;

    @Mock
    private LatestDriverLocationStore latestDriverLocationStore;

    @Mock
    private TripLocationNotifier tripLocationNotifier;

    private TripDriverLocationBootstrapService service;

    @BeforeEach
    void setUp() {
        service = new TripDriverLocationBootstrapService(
                driverAvailabilityStore,
                latestDriverLocationStore,
                tripLocationNotifier
        );
    }

    @Test
    void bootstrapCachesAndBroadcastsOnlineDriverLocationForAcceptedTrip() {
        Instant updatedAt = Instant.parse("2026-07-24T10:15:30Z");
        when(driverAvailabilityStore.findLocation(20L)).thenReturn(Optional.of(
                new DriverAvailabilityStore.DriverLocation(
                        20L,
                        BigDecimal.valueOf(10.7769),
                        BigDecimal.valueOf(106.7009),
                        updatedAt
                )
        ));

        service.bootstrap(99L, 20L);

        ArgumentCaptor<LatestDriverLocationStore.LatestDriverLocation> cacheCaptor =
                ArgumentCaptor.forClass(LatestDriverLocationStore.LatestDriverLocation.class);
        ArgumentCaptor<DriverLocationResponse> responseCaptor =
                ArgumentCaptor.forClass(DriverLocationResponse.class);
        verify(latestDriverLocationStore).save(cacheCaptor.capture());
        verify(tripLocationNotifier).broadcastDriverLocation(eq(99L), responseCaptor.capture());
        assertThat(cacheCaptor.getValue()).isEqualTo(new LatestDriverLocationStore.LatestDriverLocation(
                99L,
                20L,
                BigDecimal.valueOf(10.7769),
                BigDecimal.valueOf(106.7009),
                null,
                null,
                updatedAt
        ));
        assertThat(responseCaptor.getValue()).isEqualTo(new DriverLocationResponse(
                99L,
                20L,
                BigDecimal.valueOf(10.7769),
                BigDecimal.valueOf(106.7009),
                null,
                null,
                updatedAt
        ));
    }

    @Test
    void bootstrapSkipsWhenOnlineDriverLocationIsUnavailable() {
        when(driverAvailabilityStore.findLocation(20L)).thenReturn(Optional.empty());

        service.bootstrap(99L, 20L);

        verifyNoInteractions(latestDriverLocationStore, tripLocationNotifier);
    }

    @Test
    void bootstrapDoesNotFailAcceptFlowWhenAvailabilityStoreFails() {
        when(driverAvailabilityStore.findLocation(20L)).thenThrow(new IllegalStateException("Redis unavailable"));

        service.bootstrap(99L, 20L);

        verifyNoInteractions(latestDriverLocationStore, tripLocationNotifier);
    }

    @Test
    void bootstrapStillBroadcastsWhenLatestLocationCacheFails() {
        Instant updatedAt = Instant.parse("2026-07-24T10:15:30Z");
        when(driverAvailabilityStore.findLocation(20L)).thenReturn(Optional.of(
                new DriverAvailabilityStore.DriverLocation(
                        20L,
                        BigDecimal.valueOf(10.7769),
                        BigDecimal.valueOf(106.7009),
                        updatedAt
                )
        ));
        doThrow(new IllegalStateException("Redis unavailable"))
                .when(latestDriverLocationStore).save(any());

        service.bootstrap(99L, 20L);

        verify(tripLocationNotifier).broadcastDriverLocation(eq(99L), any(DriverLocationResponse.class));
    }
}