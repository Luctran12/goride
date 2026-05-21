package com.example.goride.driver.dto;

import com.example.goride.booking.domain.TripStatus;

public record DriverTripResponse(
        Long tripId,
        TripStatus status
) {
}
