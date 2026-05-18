package com.example.goride.booking.dto;

import com.example.goride.driver.domain.VehicleType;

import java.math.BigDecimal;

public record FareEstimateResponse(
        VehicleType vehicleType,
        BigDecimal distanceKm,
        int durationMinutes,
        BigDecimal estimatedFare,
        String currency
) {
    public static FareEstimateResponse of(
            VehicleType vehicleType,
            BigDecimal distanceKm,
            int durationMinutes,
            BigDecimal estimatedFare
    ) {
        return new FareEstimateResponse(vehicleType, distanceKm, durationMinutes, estimatedFare, "VND");
    }
}
