package com.example.goride.servicearea.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ServiceAreaCoordinateRequest(
        @NotNull @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0") BigDecimal lat,
        @NotNull @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0") BigDecimal lng
) {
}