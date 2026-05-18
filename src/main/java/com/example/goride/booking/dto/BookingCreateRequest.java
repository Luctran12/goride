package com.example.goride.booking.dto;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.driver.domain.VehicleType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record BookingCreateRequest(
        @Valid
        @NotNull
        BookingLocationRequest pickup,

        @Valid
        @NotNull
        BookingLocationRequest dropoff,

        @NotNull
        VehicleType vehicleType,

        @NotNull
        PaymentMethod paymentMethod
) {
    public BookingEstimateRequest toEstimateRequest() {
        return new BookingEstimateRequest(pickup, dropoff, vehicleType);
    }
}
