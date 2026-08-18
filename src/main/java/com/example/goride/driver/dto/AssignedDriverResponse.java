package com.example.goride.driver.dto;

import com.example.goride.driver.domain.DriverProfile;
import com.example.goride.driver.domain.VehicleType;

import java.math.BigDecimal;

public record AssignedDriverResponse(
        Long id,
        String fullName,
        String portraitUrl,
        BigDecimal averageRating,
        int totalRatings,
        int totalTrips,
        String vehiclePlate,
        VehicleType vehicleType,
        String vehicleBrand,
        String vehicleModel,
        String vehicleColor,
        Short vehicleYear
) {
    public static AssignedDriverResponse from(DriverProfile profile) {
        return new AssignedDriverResponse(
                profile.getUser().getId(),
                profile.getUser().getFullName(),
                profile.getPortraitUrl(),
                profile.getAverageRating(),
                profile.getTotalRatings(),
                profile.getTotalTrips(),
                profile.getVehiclePlate(),
                profile.getVehicleType(),
                profile.getVehicleBrand(),
                profile.getVehicleModel(),
                profile.getVehicleColor(),
                profile.getVehicleYear()
        );
    }
}
