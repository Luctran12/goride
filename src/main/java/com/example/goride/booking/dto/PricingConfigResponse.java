package com.example.goride.booking.dto;

import com.example.goride.booking.domain.PricingConfig;
import com.example.goride.driver.domain.VehicleType;

import java.math.BigDecimal;
import java.time.Instant;

public record PricingConfigResponse(
        Long id,
        VehicleType vehicleType,
        BigDecimal baseFare,
        BigDecimal perKmRate,
        BigDecimal perMinuteRate,
        BigDecimal minimumFare,
        BigDecimal surgeMultiplier,
        boolean active,
        Instant effectiveFrom,
        Instant createdAt,
        String currency
) {
    private static final String DEFAULT_CURRENCY = "VND";

    public static PricingConfigResponse from(PricingConfig pricingConfig) {
        return new PricingConfigResponse(
                pricingConfig.getId(),
                pricingConfig.getVehicleType(),
                pricingConfig.getBaseFare(),
                pricingConfig.getPerKmRate(),
                pricingConfig.getPerMinuteRate(),
                pricingConfig.getMinimumFare(),
                pricingConfig.getSurgeMultiplier(),
                pricingConfig.isActive(),
                pricingConfig.getEffectiveFrom(),
                pricingConfig.getCreatedAt(),
                DEFAULT_CURRENCY
        );
    }
}
