package com.example.goride.driver.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DriverStatusUpdateRequest(
        @NotNull
        Boolean online,

        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        BigDecimal lat,

        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        BigDecimal lng
) {
}
