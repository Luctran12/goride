package com.example.goride.payment.provider;

import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestClientMoMoPaymentClientTests {

    @Test
    void productionTimeoutIsAtLeastThirtySeconds() {
        assertThat(RestClientMoMoPaymentClient.PROVIDER_TIMEOUT)
                .isGreaterThanOrEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void mapsHttpErrorToPaymentProviderError() throws Exception {
        HttpServer server = startServer(exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        try {
            RestClientMoMoPaymentClient client = new RestClientMoMoPaymentClient(
                    RestClient.builder(),
                    Duration.ofSeconds(1)
            );

            assertProviderError(() -> client.createPayment(url(server), request()));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsReadTimeoutToPaymentProviderError() throws Exception {
        HttpServer server = startServer(exchange -> {
            try {
                Thread.sleep(250);
                byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        try {
            RestClientMoMoPaymentClient client = new RestClientMoMoPaymentClient(
                    RestClient.builder(),
                    Duration.ofMillis(50)
            );

            assertProviderError(() -> client.createPayment(url(server), request()));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsInvalidCheckoutUrlToPaymentProviderError() {
        RestClientMoMoPaymentClient client = new RestClientMoMoPaymentClient(
                RestClient.builder(),
                Duration.ofSeconds(1)
        );

        assertProviderError(() -> client.createPayment("not a valid url", request()));
    }

    private HttpServer startServer(com.sun.net.httpserver.HttpHandler handler) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/gateway/api/create", handler);
        server.start();
        return server;
    }

    private String url(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v2/gateway/api/create";
    }

    private MoMoCreatePaymentRequest request() {
        return new MoMoCreatePaymentRequest(
                "GORIDE",
                "captureWallet",
                "https://api.goride.test/momo/ipn",
                "https://app.goride.test/momo/return",
                "GORIDE-PAY-70",
                20000,
                "GoRide trip 99 payment 70",
                "GORIDE-CREATE-70",
                "",
                "signature",
                "vi"
        );
    }

    private void assertProviderError(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.PAYMENT_PROVIDER_ERROR)
                );
    }
}
