package com.example.goride.booking.service.distance;

import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OsrmRouteGeometryProviderTests {
    private static final Location ORIGIN =
            new Location(BigDecimal.valueOf(10.7700), BigDecimal.valueOf(106.7000));
    private static final Location DESTINATION =
            new Location(BigDecimal.valueOf(10.7769), BigDecimal.valueOf(106.7009));

    @Test
    void mapsGeoJsonGeometryAndManeuverSteps() throws Exception {
        AtomicReference<String> requestPath = new AtomicReference<>();
        HttpServer server = startServer(exchange -> {
            requestPath.set(exchange.getRequestURI().toString());
            byte[] body = """
                    {
                      "code": "Ok",
                      "routes": [{
                        "distance": 2345.6,
                        "duration": 456.7,
                        "geometry": {
                          "type": "LineString",
                          "coordinates": [
                            [106.7, 10.77],
                            [106.7005, 10.773],
                            [106.7009, 10.7769]
                          ]
                        },
                        "legs": [{
                          "steps": [{
                            "distance": 125.4,
                            "duration": 32.2,
                            "name": "Le Loi",
                            "maneuver": {
                              "type": "turn",
                              "modifier": "right",
                              "location": [106.7005, 10.773]
                            }
                          }]
                        }]
                      }]
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        try {
            OsrmRouteGeometryProvider provider = provider(enabledProperties(server));

            RoutePlan route = provider.route(ORIGIN, DESTINATION);

            assertThat(requestPath.get()).isEqualTo(
                    "/route/v1/driving/106.7,10.77;106.7009,10.7769"
                            + "?overview=full&geometries=geojson&steps=true"
            );
            assertThat(route.distanceMeters()).isEqualTo(2346);
            assertThat(route.durationSeconds()).isEqualTo(457);
            assertThat(route.geometry().type()).isEqualTo("LineString");
            assertThat(route.geometry().coordinates()).containsExactly(
                    java.util.List.of(BigDecimal.valueOf(106.7), BigDecimal.valueOf(10.77)),
                    java.util.List.of(BigDecimal.valueOf(106.7005), BigDecimal.valueOf(10.773)),
                    java.util.List.of(BigDecimal.valueOf(106.7009), BigDecimal.valueOf(10.7769))
            );
            assertThat(route.steps()).singleElement().satisfies(step -> {
                assertThat(step.distanceMeters()).isEqualTo(125);
                assertThat(step.durationSeconds()).isEqualTo(32);
                assertThat(step.roadName()).isEqualTo("Le Loi");
                assertThat(step.maneuverType()).isEqualTo("turn");
                assertThat(step.maneuverModifier()).isEqualTo("right");
                assertThat(step.longitude()).isEqualByComparingTo("106.7005");
                assertThat(step.latitude()).isEqualByComparingTo("10.773");
            });
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsRoutingWhenProviderIsDisabled() {
        OsrmRouteGeometryProvider provider = provider(new RoutingProperties());

        assertThatThrownBy(() -> provider.route(ORIGIN, DESTINATION))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.ROUTING_PROVIDER_ERROR)
                );
    }

    @Test
    void rejectsInvalidProviderGeometry() throws Exception {
        HttpServer server = startServer(exchange -> {
            byte[] body = """
                    {
                      "code": "Ok",
                      "routes": [{
                        "distance": 100,
                        "duration": 20,
                        "geometry": {"type": "LineString", "coordinates": [[106.7, 10.77]]},
                        "legs": []
                      }]
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        try {
            OsrmRouteGeometryProvider provider = provider(enabledProperties(server));

            assertThatThrownBy(() -> provider.route(ORIGIN, DESTINATION))
                    .isInstanceOfSatisfying(BusinessException.class, exception ->
                            assertThat(exception.errorCode()).isEqualTo(ErrorCode.ROUTING_PROVIDER_ERROR)
                    );
        } finally {
            server.stop(0);
        }
    }

    private OsrmRouteGeometryProvider provider(RoutingProperties properties) {
        return new OsrmRouteGeometryProvider(properties, RestClient.builder(), Duration.ofSeconds(1));
    }

    private RoutingProperties enabledProperties(HttpServer server) {
        RoutingProperties properties = new RoutingProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        return properties;
    }

    private HttpServer startServer(com.sun.net.httpserver.HttpHandler handler) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/route/v1/driving", handler);
        server.start();
        return server;
    }
}
