package com.example.goride.booking.service.distance;

import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
public class RoutingDistanceService implements DistanceService, ProviderRouteEstimateService {
    private static final Logger log = LoggerFactory.getLogger(RoutingDistanceService.class);
    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double ROAD_FACTOR = 1.25;
    private static final double AVERAGE_CITY_SPEED_KMH = 25.0;

    private final RoutingProperties routingProperties;
    private final RouteEstimateCache routeEstimateCache;
    private final RestClient restClient;

    @Autowired
    public RoutingDistanceService(
            RoutingProperties routingProperties,
            RouteEstimateCache routeEstimateCache,
            RestClient.Builder restClientBuilder
    ) {
        this(routingProperties, routeEstimateCache, restClientBuilder, routingProperties.timeout());
    }

    RoutingDistanceService(
            RoutingProperties routingProperties,
            RouteEstimateCache routeEstimateCache,
            RestClient.Builder restClientBuilder,
            Duration timeout
    ) {
        this.routingProperties = routingProperties;
        this.routeEstimateCache = routeEstimateCache;
        if (routingProperties.isEnabled()) {
            routingProperties.normalizedBaseUrl();
            routingProperties.normalizedProfile();
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.restClient = restClientBuilder.clone()
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public DistanceEstimate estimate(Location pickup, Location dropoff) {
        if (!routingProperties.isEnabled()) {
            return fallbackEstimate(pickup, dropoff);
        }

        Optional<DistanceEstimate> providerEstimate = estimateProviderRoute(pickup, dropoff);
        if (providerEstimate.isPresent()) {
            return providerEstimate.get();
        }
        if (routingProperties.isFallbackEnabled()) {
            log.warn("Routing provider unavailable; using configured distance fallback");
            return fallbackEstimate(pickup, dropoff);
        }
        throw new BusinessException(
                ErrorCode.ROUTING_PROVIDER_ERROR,
                "Unable to estimate route distance and duration"
        );
    }

    @Override
    public Optional<DistanceEstimate> estimateProviderRoute(Location pickup, Location dropoff) {
        if (!routingProperties.isEnabled()) {
            return Optional.empty();
        }

        String routingProfile = routingProperties.normalizedProfile();
        Optional<DistanceEstimate> cachedEstimate = findCachedEstimate(
                routingProfile,
                pickup,
                dropoff
        );
        if (cachedEstimate.isPresent()) {
            return cachedEstimate;
        }

        try {
            OsrmRouteResponse response = restClient.get()
                    .uri(routeUri(pickup, dropoff))
                    .retrieve()
                    .body(OsrmRouteResponse.class);
            DistanceEstimate estimate = routeEstimate(response);
            cacheEstimate(routingProfile, pickup, dropoff, estimate);
            return Optional.of(estimate);
        } catch (RestClientException | IllegalArgumentException exception) {
            log.warn("Routing provider estimate failed error={}", exception.getMessage());
            return Optional.empty();
        }
    }

    private Optional<DistanceEstimate> findCachedEstimate(
            String routingProfile,
            Location pickup,
            Location dropoff
    ) {
        try {
            return routeEstimateCache.find(routingProfile, pickup, dropoff);
        } catch (RuntimeException exception) {
            log.warn("Route estimate cache read failed; calling provider error={}", exception.getMessage());
            return Optional.empty();
        }
    }

    private void cacheEstimate(
            String routingProfile,
            Location pickup,
            Location dropoff,
            DistanceEstimate estimate
    ) {
        try {
            routeEstimateCache.put(routingProfile, pickup, dropoff, estimate);
        } catch (RuntimeException exception) {
            log.warn("Route estimate cache write failed; returning provider result error={}", exception.getMessage());
        }
    }

    private URI routeUri(Location pickup, Location dropoff) {
        String baseUrl = routingProperties.normalizedBaseUrl();
        String coordinates = coordinate(pickup) + ";" + coordinate(dropoff);
        return URI.create(baseUrl
                + "/route/v1/"
                + routingProperties.normalizedProfile()
                + "/"
                + coordinates
                + "?overview=false&steps=false");
    }

    private String coordinate(Location location) {
        return location.longitude().stripTrailingZeros().toPlainString()
                + ","
                + location.latitude().stripTrailingZeros().toPlainString();
    }

    private DistanceEstimate routeEstimate(OsrmRouteResponse response) {
        if (response == null
                || !"Ok".equals(response.code())
                || response.routes() == null
                || response.routes().isEmpty()) {
            throw new IllegalArgumentException("Routing provider returned no route");
        }
        OsrmRoute route = response.routes().get(0);
        if (route == null
                || route.distance() == null
                || route.duration() == null
                || !Double.isFinite(route.distance())
                || !Double.isFinite(route.duration())
                || route.distance() <= 0
                || route.duration() <= 0) {
            throw new IllegalArgumentException("Routing provider returned an invalid route");
        }
        BigDecimal distanceKm = BigDecimal.valueOf(route.distance())
                .divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP);
        int durationMinutes = Math.max(1, (int) Math.ceil(route.duration() / 60));
        return new DistanceEstimate(distanceKm, durationMinutes);
    }

    private DistanceEstimate fallbackEstimate(Location pickup, Location dropoff) {
        double straightLineKm = haversineKm(
                pickup.latitude().doubleValue(),
                pickup.longitude().doubleValue(),
                dropoff.latitude().doubleValue(),
                dropoff.longitude().doubleValue()
        );
        double roadDistanceKm = Math.max(0.1, straightLineKm * ROAD_FACTOR);
        int durationMinutes = Math.max(1, (int) Math.ceil(roadDistanceKm / AVERAGE_CITY_SPEED_KMH * 60));
        return new DistanceEstimate(
                BigDecimal.valueOf(roadDistanceKm).setScale(2, RoundingMode.HALF_UP),
                durationMinutes
        );
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    private record OsrmRouteResponse(
            String code,
            List<OsrmRoute> routes
    ) {
    }

    private record OsrmRoute(
            Double distance,
            Double duration
    ) {
    }
}
