package com.example.goride.booking.dto;

import com.example.goride.booking.service.distance.Location;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record BookingLocationRequest(
        @NotNull
        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        BigDecimal lat,

        @NotNull
        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        BigDecimal lng,

        @NotBlank
        @Size(max = 300)
        String address
) {
    public Location toLocation() {
        return new Location(lat, lng);
    }
}
