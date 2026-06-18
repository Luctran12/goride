package com.example.goride.common.api;

import com.example.goride.common.error.ErrorCode;
import com.example.goride.common.logging.RequestCorrelationFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorResponseTests {
    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void includesCurrentRequestId() {
        MDC.put(RequestCorrelationFilter.REQUEST_ID_MDC_KEY, "request-123");

        ErrorResponse response = ErrorResponse.of(
                ErrorCode.VALIDATION_ERROR,
                "Request is invalid",
                Map.of("phone", "must not be blank")
        );

        assertThat(response.requestId()).isEqualTo("request-123");
        assertThat(response.error().code()).isEqualTo("VALIDATION_ERROR");
    }
}
