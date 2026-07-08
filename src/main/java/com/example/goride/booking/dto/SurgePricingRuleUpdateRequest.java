package com.example.goride.booking.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record SurgePricingRuleUpdateRequest(
        @Size(max = 120) String name,
        @Min(1) Integer minDemandTrips,
        @DecimalMin(value = "0.1") BigDecimal minDemandSupplyRatio,
        @DecimalMin(value = "1.0") @DecimalMax(value = "3.0") BigDecimal multiplier,
        Boolean active,
        Instant startsAt,
        Instant endsAt
) {
}