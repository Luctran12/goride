package com.example.goride.driver.dto;

import com.example.goride.booking.domain.TripStatus;
import jakarta.validation.constraints.NotNull;

public record DriverTripStatusUpdateRequest(
        @NotNull TripStatus status
) {
}
