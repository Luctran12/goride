package com.example.goride.tracking.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DriverLocationUpdateRequest(
        @NotNull
        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        BigDecimal lat,

        @NotNull
        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        BigDecimal lng,

        @DecimalMin("0.0")
        @DecimalMax("360.0")
        BigDecimal bearing,

        @DecimalMin("0.0")
        BigDecimal speed
) {
}
