package com.example.goride.matching.service;

import com.example.goride.booking.service.distance.DistanceEstimate;
import com.example.goride.booking.service.distance.Location;
import com.example.goride.booking.service.distance.ProviderRouteEstimateService;
import com.example.goride.driver.domain.VehicleType;
import com.example.goride.matching.config.MatchingRouteEtaProperties;
import com.example.goride.matching.domain.DriverCandidate;
import com.example.goride.matching.domain.MatchingRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RouteEtaDriverStrategyTests {
    private static final Location PICKUP = location(10.7769, 106.7009);

    private ProviderRouteEstimateService routeEstimateService;
    private MatchingRouteEtaProperties properties;
    private RouteEtaDriverStrategy strategy;

    @BeforeEach
    void setUp() {
        routeEstimateService = mock(ProviderRouteEstimateService.class);
        properties = new MatchingRouteEtaProperties();
        properties.setEnabled(true);
        strategy = new RouteEtaDriverStrategy(
                new NearestDriverStrategy(),
                routeEstimateService,
                properties,
                Runnable::run
        );
    }

    @Test
    void ranksBoundedShortlistByProviderEta() {
        DriverCandidate nearest = candidate(10L, 200, 10.7701, 106.7011);
        DriverCandidate fastest = candidate(11L, 400, 10.7702, 106.7012);
        DriverCandidate middle = candidate(12L, 600, 10.7703, 106.7013);
        when(routeEstimateService.estimateProviderRoute(location(nearest), PICKUP))
                .thenReturn(estimate(8));
        when(routeEstimateService.estimateProviderRoute(location(fastest), PICKUP))
                .thenReturn(estimate(3));
        when(routeEstimateService.estimateProviderRoute(location(middle), PICKUP))
                .thenReturn(estimate(5));

        List<DriverCandidate> ranked = strategy.rank(request(3), List.of(nearest, fastest, middle));

        assertThat(ranked).extracting(DriverCandidate::driverId).containsExactly(11L, 12L, 10L);
    }

    @Test
    void preservesDistanceOrderWhenAnyRouteIsUnavailable() {
        DriverCandidate nearest = candidate(10L, 200, 10.7701, 106.7011);
        DriverCandidate second = candidate(11L, 400, 10.7702, 106.7012);
        DriverCandidate third = candidate(12L, 600, 10.7703, 106.7013);
        when(routeEstimateService.estimateProviderRoute(location(nearest), PICKUP))
                .thenReturn(estimate(8));
        when(routeEstimateService.estimateProviderRoute(location(second), PICKUP))
                .thenReturn(Optional.empty());

        List<DriverCandidate> ranked = strategy.rank(request(3), List.of(third, second, nearest));

        assertThat(ranked).extracting(DriverCandidate::driverId).containsExactly(10L, 11L, 12L);
        verify(routeEstimateService).estimateProviderRoute(location(third), PICKUP);
    }

    @Test
    void preservesDistanceOrderWhenProviderThrows() {
        DriverCandidate nearest = candidate(10L, 200, 10.7701, 106.7011);
        DriverCandidate second = candidate(11L, 400, 10.7702, 106.7012);
        when(routeEstimateService.estimateProviderRoute(location(nearest), PICKUP))
                .thenThrow(new IllegalStateException("provider unavailable"));

        List<DriverCandidate> ranked = strategy.rank(request(2), List.of(second, nearest));

        assertThat(ranked).extracting(DriverCandidate::driverId).containsExactly(10L, 11L);
    }

    @Test
    void onlyRoutesConfiguredTopCandidates() {
        properties.setCandidateLimit(2);
        DriverCandidate nearest = candidate(10L, 200, 10.7701, 106.7011);
        DriverCandidate second = candidate(11L, 400, 10.7702, 106.7012);
        DriverCandidate third = candidate(12L, 600, 10.7703, 106.7013);
        when(routeEstimateService.estimateProviderRoute(location(nearest), PICKUP))
                .thenReturn(estimate(8));
        when(routeEstimateService.estimateProviderRoute(location(second), PICKUP))
                .thenReturn(estimate(3));

        List<DriverCandidate> ranked = strategy.rank(request(3), List.of(nearest, second, third));

        assertThat(ranked).extracting(DriverCandidate::driverId).containsExactly(11L, 10L, 12L);
        verify(routeEstimateService, never()).estimateProviderRoute(location(third), PICKUP);
    }

    @Test
    void usesProviderDistanceAsTieBreakerForEqualRoundedEta() {
        DriverCandidate nearest = candidate(10L, 200, 10.7701, 106.7011);
        DriverCandidate shorterRoute = candidate(11L, 400, 10.7702, 106.7012);
        when(routeEstimateService.estimateProviderRoute(location(nearest), PICKUP))
                .thenReturn(estimate(5, "3.00"));
        when(routeEstimateService.estimateProviderRoute(location(shorterRoute), PICKUP))
                .thenReturn(estimate(5, "2.00"));

        List<DriverCandidate> ranked = strategy.rank(request(2), List.of(nearest, shorterRoute));

        assertThat(ranked).extracting(DriverCandidate::driverId).containsExactly(11L, 10L);
    }

    @Test
    void preservesDistanceOrderWhenExecutorRejectsWork() {
        RouteEtaDriverStrategy rejectedStrategy = new RouteEtaDriverStrategy(
                new NearestDriverStrategy(),
                routeEstimateService,
                properties,
                command -> {
                    throw new java.util.concurrent.RejectedExecutionException("queue full");
                }
        );

        List<DriverCandidate> ranked = rejectedStrategy.rank(request(2), List.of(
                candidate(11L, 400, 10.7702, 106.7012),
                candidate(10L, 200, 10.7701, 106.7011)
        ));

        assertThat(ranked).extracting(DriverCandidate::driverId).containsExactly(10L, 11L);
        verifyNoInteractions(routeEstimateService);
    }
    @Test
    void usesDistanceOrderWithoutProviderCallsWhenDisabled() {
        properties.setEnabled(false);

        List<DriverCandidate> ranked = strategy.rank(request(2), List.of(
                candidate(11L, 400, 10.7702, 106.7012),
                candidate(10L, 200, 10.7701, 106.7011)
        ));

        assertThat(ranked).extracting(DriverCandidate::driverId).containsExactly(10L, 11L);
        verifyNoInteractions(routeEstimateService);
    }

    @Test
    void missingCandidateCoordinatesUseDistanceFallback() {
        DriverCandidate withoutCoordinates = new DriverCandidate(
                10L,
                200,
                VehicleType.MOTORBIKE,
                BigDecimal.valueOf(5),
                "Driver 10",
                null
        );

        List<DriverCandidate> ranked = strategy.rank(request(2), List.of(
                candidate(11L, 400, 10.7702, 106.7012),
                withoutCoordinates
        ));

        assertThat(ranked).extracting(DriverCandidate::driverId).containsExactly(10L, 11L);
        verifyNoInteractions(routeEstimateService);
    }

    private MatchingRequest request(int limit) {
        return new MatchingRequest(
                99L,
                VehicleType.MOTORBIKE,
                PICKUP.latitude(),
                PICKUP.longitude(),
                BigDecimal.valueOf(5),
                limit
        );
    }

    private DriverCandidate candidate(Long driverId, long distanceMeters, double latitude, double longitude) {
        return new DriverCandidate(
                driverId,
                distanceMeters,
                BigDecimal.valueOf(latitude),
                BigDecimal.valueOf(longitude),
                VehicleType.MOTORBIKE,
                BigDecimal.valueOf(5),
                "Driver " + driverId,
                null
        );
    }

    private static Location location(DriverCandidate candidate) {
        return new Location(candidate.latitude(), candidate.longitude());
    }

    private static Location location(double latitude, double longitude) {
        return new Location(BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude));
    }

    private Optional<DistanceEstimate> estimate(int durationMinutes) {
        return estimate(durationMinutes, "1.00");
    }

    private Optional<DistanceEstimate> estimate(int durationMinutes, String distanceKm) {
        return Optional.of(new DistanceEstimate(new BigDecimal(distanceKm), durationMinutes));
    }
}
