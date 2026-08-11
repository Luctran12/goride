package com.example.goride.matching.service;

import com.example.goride.booking.service.distance.DistanceEstimate;
import com.example.goride.booking.service.distance.Location;
import com.example.goride.booking.service.distance.ProviderRouteEstimateService;
import com.example.goride.matching.config.MatchingRouteEtaProperties;
import com.example.goride.matching.domain.DriverCandidate;
import com.example.goride.matching.domain.MatchingRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Primary
@Component
public class RouteEtaDriverStrategy implements DriverMatchingStrategy {
    private static final Logger log = LoggerFactory.getLogger(RouteEtaDriverStrategy.class);

    private final NearestDriverStrategy nearestDriverStrategy;
    private final ProviderRouteEstimateService routeEstimateService;
    private final MatchingRouteEtaProperties properties;
    private final Executor routeEtaExecutor;

    public RouteEtaDriverStrategy(
            NearestDriverStrategy nearestDriverStrategy,
            ProviderRouteEstimateService routeEstimateService,
            MatchingRouteEtaProperties properties,
            @Qualifier("routeEtaExecutor") Executor routeEtaExecutor
    ) {
        this.nearestDriverStrategy = nearestDriverStrategy;
        this.routeEstimateService = routeEstimateService;
        this.properties = properties;
        this.routeEtaExecutor = routeEtaExecutor;
    }

    @Override
    public List<DriverCandidate> rank(MatchingRequest request, List<DriverCandidate> candidates) {
        List<DriverCandidate> distanceRanked = nearestDriverStrategy.rank(request, candidates);
        if (!properties.isEnabled() || distanceRanked.size() < 2) {
            return distanceRanked;
        }

        int routeCandidateCount = Math.min(properties.getCandidateLimit(), distanceRanked.size());
        if (routeCandidateCount < 2) {
            return distanceRanked;
        }
        List<DriverCandidate> routeCandidates = distanceRanked.subList(0, routeCandidateCount);
        if (routeCandidates.stream().anyMatch(this::hasMissingCoordinates)) {
            return distanceRanked;
        }
        Location pickup = new Location(request.pickupLatitude(), request.pickupLongitude());
        List<Optional<DistanceEstimate>> estimates = estimateRoutes(routeCandidates, pickup);
        if (estimates.size() != routeCandidateCount || estimates.stream().anyMatch(Optional::isEmpty)) {
            log.info(
                    "Route ETA ranking fell back to Redis distance tripId={} candidateCount={}",
                    request.tripId(),
                    routeCandidateCount
            );
            return distanceRanked;
        }

        Map<Long, DistanceEstimate> estimateByDriver = new HashMap<>();
        for (int index = 0; index < routeCandidateCount; index++) {
            estimateByDriver.put(
                    routeCandidates.get(index).driverId(),
                    estimates.get(index).orElseThrow()
            );
        }

        List<DriverCandidate> ranked = new ArrayList<>(distanceRanked);
        ranked.subList(0, routeCandidateCount).sort(Comparator
                .comparingInt((DriverCandidate candidate) ->
                        estimateByDriver.get(candidate.driverId()).durationMinutes())
                .thenComparing(candidate -> estimateByDriver.get(candidate.driverId()).distanceKm())
                .thenComparingLong(DriverCandidate::distanceMeters)
                .thenComparing(DriverCandidate::driverId));
        return List.copyOf(ranked);
    }

    private List<Optional<DistanceEstimate>> estimateRoutes(
            List<DriverCandidate> candidates,
            Location pickup
    ) {
        try {
            List<CompletableFuture<Optional<DistanceEstimate>>> futures = candidates.stream()
                    .map(candidate -> CompletableFuture.supplyAsync(
                            () -> estimateRoute(candidate, pickup),
                            routeEtaExecutor
                    ))
                    .toList();
            return futures.stream()
                    .map(CompletableFuture::join)
                    .toList();
        } catch (RuntimeException exception) {
            log.warn("Route ETA executor unavailable error={}", exception.getMessage());
            return List.of();
        }
    }

    private boolean hasMissingCoordinates(DriverCandidate candidate) {
        return candidate.latitude() == null || candidate.longitude() == null;
    }

    private Optional<DistanceEstimate> estimateRoute(DriverCandidate candidate, Location pickup) {
        try {
            return routeEstimateService.estimateProviderRoute(
                    new Location(candidate.latitude(), candidate.longitude()),
                    pickup
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "Route ETA candidate estimate failed driverId={} error={}",
                    candidate.driverId(),
                    exception.getMessage()
            );
            return Optional.empty();
        }
    }
}
