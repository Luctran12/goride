package com.example.goride.common.logging;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestCorrelationFilterTests {
    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @Test
    void preservesSafeRequestIdAndClearsMdcAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/register");
        request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, "fe-register-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> requestIdInsideChain = new AtomicReference<>();
        FilterChain chain = (servletRequest, servletResponse) ->
                requestIdInsideChain.set(MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY));

        filter.doFilter(request, response, chain);

        assertThat(requestIdInsideChain).hasValue("fe-register-123");
        assertThat(response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER)).isEqualTo("fe-register-123");
        assertThat(MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY)).isNull();
    }

    @Test
    void replacesUnsafeRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/drivers/me/profile");
        request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, "unsafe\nrequest-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> generatedRequestId = new AtomicReference<>();
        FilterChain chain = (servletRequest, servletResponse) ->
                generatedRequestId.set(MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY));

        filter.doFilter(request, response, chain);

        assertThat(generatedRequestId.get())
                .isNotEqualTo("unsafe\nrequest-id")
                .matches("[a-f0-9-]{36}");
        assertThat(response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER))
                .isEqualTo(generatedRequestId.get());
    }

    @Test
    void clearsMdcWhenRequestFails() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/bookings");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> {
            throw new IllegalStateException("test failure");
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY)).isNull();
    }
}
