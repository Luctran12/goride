package com.example.goride.booking.dto;

import com.example.goride.driver.domain.VehicleType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record BookingEstimateRequest(
        @Valid
        @NotNull
        BookingLocationRequest pickup,

        @Valid
        @NotNull
        BookingLocationRequest dropoff,

        @NotNull
        VehicleType vehicleType
) {
}
