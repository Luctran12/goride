package com.example.goride.matching.domain;

import com.example.goride.driver.domain.VehicleType;

import java.math.BigDecimal;

public record DriverCandidate(
        Long driverId,
        long distanceMeters,
        BigDecimal latitude,
        BigDecimal longitude,
        VehicleType vehicleType,
        BigDecimal rating,
        String driverName,
        String avatarUrl
) {
    public DriverCandidate(
            Long driverId,
            long distanceMeters,
            VehicleType vehicleType,
            BigDecimal rating,
            String driverName,
            String avatarUrl
    ) {
        this(driverId, distanceMeters, null, null, vehicleType, rating, driverName, avatarUrl);
    }
}
