package com.example.goride.common.api;

import com.example.goride.common.error.ErrorCode;
import com.example.goride.common.logging.RequestCorrelationFilter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.Map;

@Schema(
        name = "ErrorResponse",
        description = "Stable error envelope returned by REST endpoints.",
        requiredProperties = {"success", "error", "requestId", "timestamp"},
        example = """
                {
                  "success": false,
                  "error": {
                    "code": "VALIDATION_ERROR",
                    "message": "Request is invalid",
                    "details": {"from": "Required request parameter is missing"}
                  },
                  "requestId": "01J3ANALYTICSREQUEST",
                  "timestamp": "2026-07-29T08:00:00Z"
                }
                """
)
public record ErrorResponse(
        @Schema(description = "Always false for an error envelope.", example = "false")
        boolean success,
        @Schema(description = "Machine-readable code, safe message and structured details.")
        ErrorBody error,
        @Schema(
                description = "Correlation ID when request correlation is active.",
                example = "01J3ANALYTICSREQUEST",
                types = {"string", "null"},
                nullable = true
        )
        String requestId,
        @Schema(description = "UTC time when the error envelope was created.")
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

    @Schema(
            description = "Structured API error.",
            requiredProperties = {"code", "message", "details"}
    )
    public record ErrorBody(
            @Schema(
                    description = "Stable ErrorCode enum name.",
                    example = "VALIDATION_ERROR"
            )
            String code,
            @Schema(
                    description = "Safe user-facing error summary.",
                    example = "Request is invalid"
            )
            String message,
            @Schema(
                    description = "Field or domain-specific diagnostic values. "
                            + "May be an empty object."
            )
            Map<String, Object> details
    ) {
    }
}
