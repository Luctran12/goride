package com.example.goride.matching.domain;

import com.example.goride.driver.domain.VehicleType;

import java.math.BigDecimal;

public record DriverCandidate(
        Long driverId,
        long distanceMeters,
        VehicleType vehicleType,
        BigDecimal rating,
        String driverName,
        String avatarUrl
) {
}
