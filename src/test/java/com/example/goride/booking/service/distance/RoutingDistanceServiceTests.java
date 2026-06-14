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

class RoutingDistanceServiceTests {
    private static final Location PICKUP =
            new Location(BigDecimal.valueOf(10.7700), BigDecimal.valueOf(106.7000));
    private static final Location DROPOFF =
            new Location(BigDecimal.valueOf(10.8130), BigDecimal.valueOf(106.6650));

    @Test
    void usesFallbackEstimateWhenRoutingIsDisabled() {
        RoutingProperties properties = new RoutingProperties();
        RoutingDistanceService service = service(properties);

        DistanceEstimate estimate = service.estimate(PICKUP, DROPOFF);

        assertThat(estimate.distanceKm()).isPositive();
        assertThat(estimate.durationMinutes()).isPositive();
    }

    @Test
    void mapsOsrmDistanceAndDuration() throws Exception {
        AtomicReference<String> requestPath = new AtomicReference<>();
        HttpServer server = startServer(exchange -> {
            requestPath.set(exchange.getRequestURI().toString());
            byte[] body = """
                    {"code":"Ok","routes":[{"distance":5425.6,"duration":987.1}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        try {
            RoutingProperties properties = enabledProperties(server);
            RoutingDistanceService service = service(properties);

            DistanceEstimate estimate = service.estimate(PICKUP, DROPOFF);

            assertThat(estimate.distanceKm()).isEqualByComparingTo("5.43");
            assertThat(estimate.durationMinutes()).isEqualTo(17);
            assertThat(requestPath.get()).isEqualTo(
                    "/route/v1/driving/106.7,10.77;106.665,10.813?overview=false&steps=false"
            );
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fallsBackWhenProviderReturnsNoRoute() throws Exception {
        HttpServer server = startServer(exchange -> {
            byte[] body = """
                    {"code":"NoRoute","routes":[]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        try {
            RoutingDistanceService service = service(enabledProperties(server));

            DistanceEstimate estimate = service.estimate(PICKUP, DROPOFF);

            assertThat(estimate.distanceKm()).isPositive();
            assertThat(estimate.durationMinutes()).isPositive();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fallsBackOnProviderTimeout() throws Exception {
        HttpServer server = startServer(exchange -> {
            try {
                Thread.sleep(250);
                exchange.sendResponseHeaders(500, -1);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        try {
            RoutingDistanceService service = new RoutingDistanceService(
                    enabledProperties(server),
                    RestClient.builder(),
                    Duration.ofMillis(50)
            );

            DistanceEstimate estimate = service.estimate(PICKUP, DROPOFF);

            assertThat(estimate.distanceKm()).isPositive();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void returnsBadGatewayWhenProviderFailsAndFallbackIsDisabled() throws Exception {
        HttpServer server = startServer(exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        try {
            RoutingProperties properties = enabledProperties(server);
            properties.setFallbackEnabled(false);
            RoutingDistanceService service = service(properties);

            assertThatThrownBy(() -> service.estimate(PICKUP, DROPOFF))
                    .isInstanceOfSatisfying(BusinessException.class, exception ->
                            assertThat(exception.errorCode()).isEqualTo(ErrorCode.ROUTING_PROVIDER_ERROR)
                    );
        } finally {
            server.stop(0);
        }
    }

    @Test
    void failsFastWhenEnabledProviderConfigurationIsInvalid() {
        RoutingProperties properties = new RoutingProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("not-a-url");

        assertThatThrownBy(() -> service(properties))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private RoutingDistanceService service(RoutingProperties properties) {
        return new RoutingDistanceService(
                properties,
                RestClient.builder(),
                Duration.ofSeconds(1)
        );
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
