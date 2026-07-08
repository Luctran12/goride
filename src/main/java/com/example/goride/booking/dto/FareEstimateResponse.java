package com.example.goride.booking.dto;

import com.example.goride.driver.domain.VehicleType;

import java.math.BigDecimal;

public record FareEstimateResponse(
        VehicleType vehicleType,
        BigDecimal distanceKm,
        int durationMinutes,
        BigDecimal estimatedFare,
        String currency,
        BigDecimal baseFare,
        BigDecimal pricingSurgeMultiplier,
        BigDecimal dynamicSurgeMultiplier,
        BigDecimal effectiveSurgeMultiplier,
        BigDecimal surgeAmount,
        FareSurgeResponse surge
) {
    public static FareEstimateResponse of(
            VehicleType vehicleType,
            BigDecimal distanceKm,
            int durationMinutes,
            BigDecimal estimatedFare
    ) {
        return of(
                vehicleType,
                distanceKm,
                durationMinutes,
                estimatedFare,
                estimatedFare,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                null
        );
    }

    public static FareEstimateResponse of(
            VehicleType vehicleType,
            BigDecimal distanceKm,
            int durationMinutes,
            BigDecimal estimatedFare,
            BigDecimal baseFare,
            BigDecimal pricingSurgeMultiplier,
            BigDecimal dynamicSurgeMultiplier,
            BigDecimal effectiveSurgeMultiplier,
            FareSurgeResponse surge
    ) {
        BigDecimal surgeAmount = estimatedFare.subtract(baseFare);
        return new FareEstimateResponse(
                vehicleType,
                distanceKm,
                durationMinutes,
                estimatedFare,
                "VND",
                baseFare,
                pricingSurgeMultiplier,
                dynamicSurgeMultiplier,
                effectiveSurgeMultiplier,
                surgeAmount,
                surge
        );
    }
}