package com.example.goride.common.ratelimit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RateLimitFilterTests {
    @Test
    void returnsStructuredServiceUnavailableWhenStoreFails() throws Exception {
        RateLimitProperties properties = properties();
        RateLimitStore store = mock(RateLimitStore.class);
        FilterChain filterChain = mock(FilterChain.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        RateLimitFilter filter = new RateLimitFilter(properties, store, objectMapper, meterRegistry);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/bookings");
        request.setRemoteAddr("203.0.113.10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(store.consume("ip:203.0.113.10", properties))
                .thenThrow(new IllegalStateException("redis unavailable"));

        filter.doFilter(request, response, filterChain);

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
        assertThat(body.path("error").path("code").asText())
                .isEqualTo("RATE_LIMIT_STORE_UNAVAILABLE");
        assertThat(body.path("error").path("details").path("retryAfterSeconds").asLong())
                .isEqualTo(1L);
        assertThat(meterRegistry.counter(
                "goride.rate.limit.requests",
                "outcome",
                "store_error"
        ).count()).isEqualTo(1.0);
        verifyNoInteractions(filterChain);
    }

    private RateLimitProperties properties() {
        return new RateLimitProperties(
                true,
                RateLimitProperties.Store.MEMORY,
                10,
                10,
                60,
                1_000,
                "test:rate-limit:",
                List.of("/actuator/**"),
                false
        );
    }
}
