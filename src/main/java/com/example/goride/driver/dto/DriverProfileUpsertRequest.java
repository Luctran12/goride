package com.example.goride.driver.dto;

import com.example.goride.driver.domain.VehicleType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record DriverProfileUpsertRequest(
        @NotBlank
        @Size(max = 30)
        String licenseNumber,

        @NotNull
        @Future
        LocalDate licenseExpiry,

        @NotBlank
        @Size(max = 20)
        String idCardNumber,

        @NotBlank
        @Size(max = 500)
        String portraitUrl,

        @Size(max = 500)
        String licenseImageUrl,

        @Size(max = 500)
        String idCardImageUrl,

        @Size(max = 500)
        String vehicleRegistrationUrl,

        @NotBlank
        @Size(max = 30)
        String vehiclePlate,

        @NotNull
        VehicleType vehicleType,

        @Size(max = 50)
        String vehicleBrand,

        @Size(max = 50)
        String vehicleModel,

        @Size(max = 30)
        String vehicleColor,

        Short vehicleYear
) {
    public DriverProfileUpsertRequest(
            String licenseNumber,
            LocalDate licenseExpiry,
            String idCardNumber,
            String portraitUrl,
            String vehiclePlate,
            VehicleType vehicleType,
            String vehicleBrand,
            String vehicleModel,
            String vehicleColor,
            Short vehicleYear
    ) {
        this(
                licenseNumber,
                licenseExpiry,
                idCardNumber,
                portraitUrl,
                null,
                null,
                null,
                vehiclePlate,
                vehicleType,
                vehicleBrand,
                vehicleModel,
                vehicleColor,
                vehicleYear
        );
    }
}