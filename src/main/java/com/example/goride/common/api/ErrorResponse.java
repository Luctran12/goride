package com.example.goride.common.api;

import com.example.goride.common.error.ErrorCode;
import com.example.goride.common.logging.RequestCorrelationFilter;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
        boolean success,
        ErrorBody error,
        String requestId,
        Instant timestamp
) {
    public static ErrorResponse of(ErrorCode code, String message, Map<String, Object> details) {
        return new ErrorResponse(
                false,
                new ErrorBody(code.name(), message, details == null ? Map.of() : details),
                MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY),
                Instant.now()
        );
    }

    public record ErrorBody(
            String code,
            String message,
            Map<String, Object> details
    ) {
    }
}
