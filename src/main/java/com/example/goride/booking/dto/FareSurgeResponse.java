package com.example.goride.booking.dto;

import com.example.goride.driver.domain.VehicleType;

import java.math.BigDecimal;

public record FareSurgeResponse(
        VehicleType vehicleType,
        long demandTrips,
        long onlineDrivers,
        BigDecimal demandSupplyRatio,
        BigDecimal pricingSurgeMultiplier,
        BigDecimal dynamicSurgeMultiplier,
        BigDecimal effectiveSurgeMultiplier,
        boolean surgeApplied,
        Long ruleId,
        String ruleName
) {
}