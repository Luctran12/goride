package com.example.goride.driver.service.availability;

import com.example.goride.driver.domain.VehicleType;

import java.math.BigDecimal;

public interface DriverAvailabilityStore {
    void markAvailable(DriverAvailability availability);

    void markOffline(Long driverId);

    record DriverAvailability(
            Long driverId,
            BigDecimal latitude,
            BigDecimal longitude,
            VehicleType vehicleType,
            BigDecimal rating,
            String driverName,
            String avatarUrl
    ) {
    }
}
