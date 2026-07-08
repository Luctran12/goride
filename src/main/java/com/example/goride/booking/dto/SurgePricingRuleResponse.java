package com.example.goride.booking.dto;

import com.example.goride.booking.domain.SurgePricingRule;
import com.example.goride.driver.domain.VehicleType;

import java.math.BigDecimal;
import java.time.Instant;

public record SurgePricingRuleResponse(
        Long id,
        VehicleType vehicleType,
        String name,
        int minDemandTrips,
        BigDecimal minDemandSupplyRatio,
        BigDecimal multiplier,
        boolean active,
        Instant startsAt,
        Instant endsAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static SurgePricingRuleResponse from(SurgePricingRule rule) {
        return new SurgePricingRuleResponse(
                rule.getId(),
                rule.getVehicleType(),
                rule.getName(),
                rule.getMinDemandTrips(),
                rule.getMinDemandSupplyRatio(),
                rule.getMultiplier(),
                rule.isActive(),
                rule.getStartsAt(),
                rule.getEndsAt(),
                rule.getCreatedAt(),
                rule.getUpdatedAt()
        );
    }
}