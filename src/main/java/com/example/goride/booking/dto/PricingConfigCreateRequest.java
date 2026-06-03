package com.example.goride.booking.dto;

import com.example.goride.driver.domain.VehicleType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;

public record PricingConfigCreateRequest(
        @NotNull VehicleType vehicleType,
        @NotNull @PositiveOrZero BigDecimal baseFare,
        @NotNull @PositiveOrZero BigDecimal perKmRate,
        @NotNull @PositiveOrZero BigDecimal perMinuteRate,
        @NotNull @PositiveOrZero BigDecimal minimumFare,
        @NotNull @DecimalMin(value = "0.1") BigDecimal surgeMultiplier,
        Instant effectiveFrom
) {
}
