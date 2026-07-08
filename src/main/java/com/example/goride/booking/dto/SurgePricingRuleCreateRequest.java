package com.example.goride.booking.dto;

import com.example.goride.driver.domain.VehicleType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record SurgePricingRuleCreateRequest(
        @NotNull VehicleType vehicleType,
        @NotBlank @Size(max = 120) String name,
        @NotNull @Min(1) Integer minDemandTrips,
        @NotNull @DecimalMin(value = "0.1") BigDecimal minDemandSupplyRatio,
        @NotNull @DecimalMin(value = "1.0") @DecimalMax(value = "3.0") BigDecimal multiplier,
        Boolean active,
        Instant startsAt,
        Instant endsAt
) {
}