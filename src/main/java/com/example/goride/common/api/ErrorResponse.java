package com.example.goride.common.api;

import com.example.goride.common.error.ErrorCode;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
        boolean success,
        ErrorBody error,
        Instant timestamp
) {
    public static ErrorResponse of(ErrorCode code, String message, Map<String, Object> details) {
        return new ErrorResponse(
                false,
                new ErrorBody(code.name(), message, details == null ? Map.of() : details),
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
