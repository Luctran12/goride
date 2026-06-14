package com.example.goride.booking.service.distance;

import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.List;

@Service
public class OsrmRouteGeometryProvider implements RouteGeometryProvider {
    private static final String LINE_STRING = "LineString";

    private final RoutingProperties routingProperties;
    private final RestClient restClient;

    @Autowired
    public OsrmRouteGeometryProvider(
            RoutingProperties routingProperties,
            RestClient.Builder restClientBuilder
    ) {
        this(routingProperties, restClientBuilder, routingProperties.timeout());
    }

    OsrmRouteGeometryProvider(
            RoutingProperties routingProperties,
            RestClient.Builder restClientBuilder,
            Duration timeout
    ) {
        this.routingProperties = routingProperties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.restClient = restClientBuilder.clone()
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public RoutePlan route(Location origin, Location destination) {
        if (!routingProperties.isEnabled()) {
            throw providerError("Routing provider is disabled");
        }
        try {
            OsrmRouteResponse response = restClient.get()
                    .uri(routeUri(origin, destination))
                    .retrieve()
                    .body(OsrmRouteResponse.class);
            return mapRoute(response);
        } catch (RestClientException | IllegalArgumentException exception) {
            throw providerError("Unable to calculate driver route: " + exception.getMessage());
        }
    }

    private URI routeUri(Location origin, Location destination) {
        String coordinates = coordinate(origin) + ";" + coordinate(destination);
        return URI.create(routingProperties.normalizedBaseUrl()
                + "/route/v1/"
                + routingProperties.normalizedProfile()
                + "/"
                + coordinates
                + "?overview=full&geometries=geojson&steps=true");
    }

    private String coordinate(Location location) {
        return location.longitude().stripTrailingZeros().toPlainString()
                + ","
                + location.latitude().stripTrailingZeros().toPlainString();
    }

    private RoutePlan mapRoute(OsrmRouteResponse response) {
        if (response == null
                || !"Ok".equals(response.code())
                || response.routes() == null
                || response.routes().isEmpty()) {
            throw new IllegalArgumentException("Routing provider returned no route");
        }
        OsrmRoute route = response.routes().get(0);
        validateRoute(route);
        List<RouteStep> steps = route.legs() == null
                ? List.of()
                : route.legs().stream()
                .filter(leg -> leg != null && leg.steps() != null)
                .flatMap(leg -> leg.steps().stream())
                .map(this::mapStep)
                .toList();
        return new RoutePlan(
                positiveRounded(route.distance(), "distance"),
                positiveRounded(route.duration(), "duration"),
                new RouteGeometry(LINE_STRING, immutableCoordinates(route.geometry().coordinates())),
                steps
        );
    }

    private void validateRoute(OsrmRoute route) {
        if (route == null
                || route.distance() == null
                || route.duration() == null
                || route.geometry() == null
                || !LINE_STRING.equals(route.geometry().type())
                || route.geometry().coordinates() == null
                || route.geometry().coordinates().size() < 2) {
            throw new IllegalArgumentException("Routing provider returned an invalid route");
        }
        positiveRounded(route.distance(), "distance");
        positiveRounded(route.duration(), "duration");
        immutableCoordinates(route.geometry().coordinates());
    }

    private List<List<BigDecimal>> immutableCoordinates(List<List<BigDecimal>> coordinates) {
        return coordinates.stream()
                .map(coordinate -> {
                    if (coordinate == null || coordinate.size() < 2) {
                        throw new IllegalArgumentException("Routing provider returned invalid geometry coordinates");
                    }
                    Location location = new Location(coordinate.get(1), coordinate.get(0));
                    return List.of(location.longitude(), location.latitude());
                })
                .toList();
    }

    private RouteStep mapStep(OsrmStep step) {
        if (step == null || step.maneuver() == null || step.maneuver().location() == null
                || step.maneuver().location().size() < 2) {
            throw new IllegalArgumentException("Routing provider returned an invalid route step");
        }
        Location maneuverLocation = new Location(
                step.maneuver().location().get(1),
                step.maneuver().location().get(0)
        );
        return new RouteStep(
                nonNegativeRounded(step.distance(), "step distance"),
                nonNegativeRounded(step.duration(), "step duration"),
                StringUtils.hasText(step.name()) ? step.name() : null,
                step.maneuver().type(),
                step.maneuver().modifier(),
                maneuverLocation.longitude(),
                maneuverLocation.latitude()
        );
    }

    private long positiveRounded(Double value, String field) {
        if (value == null || !Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException("Routing provider returned invalid " + field);
        }
        return Math.max(1L, Math.round(value));
    }

    private long nonNegativeRounded(Double value, String field) {
        if (value == null || !Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException("Routing provider returned invalid " + field);
        }
        return Math.round(value);
    }

    private BusinessException providerError(String message) {
        return new BusinessException(ErrorCode.ROUTING_PROVIDER_ERROR, message);
    }

    private record OsrmRouteResponse(String code, List<OsrmRoute> routes) {
    }

    private record OsrmRoute(
            Double distance,
            Double duration,
            OsrmGeometry geometry,
            List<OsrmLeg> legs
    ) {
    }

    private record OsrmGeometry(String type, List<List<BigDecimal>> coordinates) {
    }

    private record OsrmLeg(List<OsrmStep> steps) {
    }

    private record OsrmStep(
            Double distance,
            Double duration,
            String name,
            OsrmManeuver maneuver
    ) {
    }

    private record OsrmManeuver(
            String type,
            String modifier,
            List<BigDecimal> location
    ) {
    }
}
