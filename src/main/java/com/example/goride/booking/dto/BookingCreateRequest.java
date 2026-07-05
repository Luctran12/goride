package com.example.goride.booking.dto;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.driver.domain.VehicleType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

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
        PaymentMethod paymentMethod,

        Instant scheduledPickupTime
) {
    public BookingCreateRequest(
            BookingLocationRequest pickup,
            BookingLocationRequest dropoff,
            VehicleType vehicleType,
            PaymentMethod paymentMethod
    ) {
        this(pickup, dropoff, vehicleType, paymentMethod, null);
    }

    public BookingEstimateRequest toEstimateRequest() {
        return new BookingEstimateRequest(pickup, dropoff, vehicleType);
    }
}