package com.example.goride.common.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;
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
        assertCompletionMdcCleared();
    }

    @Test
    void addsHttpCompletionFieldsToLogMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/bookings");
        request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, "fe-booking-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Logger logger = (Logger) LoggerFactory.getLogger(RequestCorrelationFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            });
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list).hasSize(1);
        Map<String, String> logMdc = appender.list.get(0).getMDCPropertyMap();
        assertThat(logMdc)
                .containsEntry(RequestCorrelationFilter.REQUEST_ID_MDC_KEY, "fe-booking-123")
                .containsEntry(RequestCorrelationFilter.HTTP_METHOD_MDC_KEY, "POST")
                .containsEntry(RequestCorrelationFilter.URL_PATH_MDC_KEY, "/api/v1/bookings")
                .containsEntry(RequestCorrelationFilter.HTTP_STATUS_MDC_KEY, "200");
        assertThat(logMdc.get(RequestCorrelationFilter.EVENT_DURATION_MS_MDC_KEY)).matches("\\d+");
        assertCompletionMdcCleared();
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

        assertCompletionMdcCleared();
    }

    private void assertCompletionMdcCleared() {
        assertThat(MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY)).isNull();
        assertThat(MDC.get(RequestCorrelationFilter.HTTP_METHOD_MDC_KEY)).isNull();
        assertThat(MDC.get(RequestCorrelationFilter.URL_PATH_MDC_KEY)).isNull();
        assertThat(MDC.get(RequestCorrelationFilter.HTTP_STATUS_MDC_KEY)).isNull();
        assertThat(MDC.get(RequestCorrelationFilter.EVENT_DURATION_MS_MDC_KEY)).isNull();
    }
}
