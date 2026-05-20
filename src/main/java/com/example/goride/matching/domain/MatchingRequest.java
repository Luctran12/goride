package com.example.goride.matching.domain;

import com.example.goride.booking.event.BookingCreatedEvent;
import com.example.goride.driver.domain.VehicleType;

import java.math.BigDecimal;

public record MatchingRequest(
        Long tripId,
        VehicleType vehicleType,
        BigDecimal pickupLatitude,
        BigDecimal pickupLongitude,
        BigDecimal radiusKm,
        int limit
) {
    private static final BigDecimal DEFAULT_RADIUS_KM = BigDecimal.valueOf(5);
    private static final int DEFAULT_LIMIT = 3;

    public MatchingRequest {
        if (tripId == null) {
            throw new IllegalArgumentException("tripId must not be null");
        }
        if (vehicleType == null) {
            throw new IllegalArgumentException("vehicleType must not be null");
        }
        pickupLatitude = requireRange(pickupLatitude, "pickupLatitude", -90, 90);
        pickupLongitude = requireRange(pickupLongitude, "pickupLongitude", -180, 180);
        if (radiusKm == null || radiusKm.signum() <= 0) {
            throw new IllegalArgumentException("radiusKm must be positive");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
    }

    public static MatchingRequest from(BookingCreatedEvent event) {
        return new MatchingRequest(
                event.tripId(),
                event.vehicleType(),
                event.pickupLatitude(),
                event.pickupLongitude(),
                DEFAULT_RADIUS_KM,
                DEFAULT_LIMIT
        );
    }

    private static BigDecimal requireRange(BigDecimal value, String fieldName, int min, int max) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        if (value.compareTo(BigDecimal.valueOf(min)) < 0 || value.compareTo(BigDecimal.valueOf(max)) > 0) {
            throw new IllegalArgumentException(fieldName + " is out of range");
        }
        return value;
    }
}
