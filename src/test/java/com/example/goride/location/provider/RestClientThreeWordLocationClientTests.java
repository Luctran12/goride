package com.example.goride.location.provider;

import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestClientThreeWordLocationClientTests {
    private static final BigDecimal LATITUDE = BigDecimal.valueOf(10.7769);
    private static final BigDecimal LONGITUDE = BigDecimal.valueOf(106.7009);

    @Test
    void callsPythonToWordsWithLonParameterAndMapsResponse() throws Exception {
        AtomicReference<String> requestUri = new AtomicReference<>();
        HttpServer server = startServer(exchange -> {
            requestUri.set(exchange.getRequestURI().toString());
            respond(exchange, 200, locationJson(true));
        });
        try {
            var response = client(Duration.ofSeconds(1)).toWords(url(server), LATITUDE, LONGITUDE);

            assertThat(requestUri.get()).isEqualTo("/api/to-words?lat=10.7769&lon=106.7009");
            assertThat(response.lon()).isEqualByComparingTo(LONGITUDE);
            assertThat(response.words()).containsExactly("hoa", "la", "cay");
            assertThat(response.bounds().sw()).hasSize(2);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void callsPythonToCoordinateWithNormalizedAddress() throws Exception {
        AtomicReference<String> requestUri = new AtomicReference<>();
        HttpServer server = startServer(exchange -> {
            requestUri.set(exchange.getRequestURI().toString());
            respond(exchange, 200, locationJson(false));
        });
        try {
            var response = client(Duration.ofSeconds(1))
                    .toCoordinate(url(server), "hoa.khuon_mat.cay");

            assertThat(requestUri.get())
                    .isEqualTo("/api/to-coordinate?address=hoa.khuon_mat.cay");
            assertThat(response.address()).isEqualTo("hoa.khuon_mat.cay");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsUnsupportedCoordinatesAndKeepsProviderBounds() throws Exception {
        HttpServer server = startServer(exchange -> respond(exchange, 400, """
                {
                  "error": "Toa do nam ngoai vung ban do ho tro",
                  "bounds": {
                    "lat_min": 10.3,
                    "lat_max": 11.2,
                    "lon_min": 106.3,
                    "lon_max": 107.1
                  }
                }
                """));
        try {
            assertThatThrownBy(() -> client(Duration.ofSeconds(1))
                    .toWords(url(server), LATITUDE, LONGITUDE))
                    .isInstanceOfSatisfying(BusinessException.class, exception -> {
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.WORD_LOCATION_OUT_OF_BOUNDS);
                        assertThat(exception.details()).containsEntry("providerStatus", 400);
                        assertThat(exception.details()).containsKey("bounds");
                    });
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsBadAddressFormatToStableClientError() throws Exception {
        HttpServer server = startServer(exchange -> respond(exchange, 400, """
                {"error": "Dinh dang khong hop le"}
                """));
        try {
            assertThatThrownBy(() -> client(Duration.ofSeconds(1))
                    .toCoordinate(url(server), "hoa.la.cay"))
                    .isInstanceOfSatisfying(BusinessException.class, exception ->
                            assertThat(exception.errorCode()).isEqualTo(ErrorCode.WORD_LOCATION_INVALID_ADDRESS)
                    );
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsUnknownAddressToNotFound() throws Exception {
        HttpServer server = startServer(exchange -> respond(exchange, 404, """
                {"error": "Tu khong ton tai trong tu dien"}
                """));
        try {
            assertThatThrownBy(() -> client(Duration.ofSeconds(1))
                    .toCoordinate(url(server), "hoa.la.cay"))
                    .isInstanceOfSatisfying(BusinessException.class, exception ->
                            assertThat(exception.errorCode()).isEqualTo(ErrorCode.WORD_LOCATION_NOT_FOUND)
                    );
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsServerErrorToProviderError() throws Exception {
        HttpServer server = startServer(exchange -> respond(exchange, 500, """
                {"error": "internal"}
                """));
        try {
            assertProviderError(() -> client(Duration.ofSeconds(1))
                    .toWords(url(server), LATITUDE, LONGITUDE));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsReadTimeoutToProviderError() throws Exception {
        HttpServer server = startServer(exchange -> {
            try {
                Thread.sleep(250);
                respond(exchange, 200, locationJson(true));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                exchange.close();
            }
        });
        try {
            assertProviderError(() -> client(Duration.ofMillis(50))
                    .toWords(url(server), LATITUDE, LONGITUDE));
        } finally {
            server.stop(0);
        }
    }

    private RestClientThreeWordLocationClient client(Duration timeout) {
        return new RestClientThreeWordLocationClient(
                RestClient.builder(),
                new ObjectMapper(),
                timeout
        );
    }

    private HttpServer startServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api", handler);
        server.start();
        return server;
    }

    private String url(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private String locationJson(boolean includeWords) {
        String words = includeWords ? "\"words\":[\"hoa\",\"la\",\"cay\"]," : "";
        String address = includeWords ? "hoa.la.cay" : "hoa.khuon_mat.cay";
        return """
                {
                  "lat": 10.7769,
                  "lon": 106.7009,
                  %s
                  "address": "%s",
                  "bounds": {
                    "sw": [10.7768, 106.7008],
                    "ne": [10.7770, 106.7010]
                  }
                }
                """.formatted(words, address);
    }

    private void assertProviderError(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.WORD_LOCATION_PROVIDER_ERROR)
                );
    }
}
