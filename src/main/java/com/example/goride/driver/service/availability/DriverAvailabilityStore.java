package com.example.goride.driver.service.availability;

import com.example.goride.driver.domain.VehicleType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public interface DriverAvailabilityStore {
    void markAvailable(DriverAvailability availability);

    boolean refreshHeartbeat(DriverAvailability availability);

    Optional<DriverLocation> findLocation(Long driverId);

    void markOffline(Long driverId);

    void updateRating(Long driverId, BigDecimal rating);

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

    record DriverLocation(
            Long driverId,
            BigDecimal latitude,
            BigDecimal longitude,
            Instant updatedAt
    ) {
    }
}
