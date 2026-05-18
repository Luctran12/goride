package com.example.goride.booking.service.distance;

import java.math.BigDecimal;

public record Location(
        BigDecimal latitude,
        BigDecimal longitude
) {
    public Location {
        latitude = requireInRange(latitude, "latitude", -90, 90);
        longitude = requireInRange(longitude, "longitude", -180, 180);
    }

    private static BigDecimal requireInRange(BigDecimal value, String fieldName, int min, int max) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        if (value.compareTo(BigDecimal.valueOf(min)) < 0 || value.compareTo(BigDecimal.valueOf(max)) > 0) {
            throw new IllegalArgumentException(fieldName + " is out of range");
        }
        return value;
    }
}
