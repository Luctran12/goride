package com.example.goride.driver.dto;

import com.example.goride.driver.domain.ApprovalStatus;
import com.example.goride.driver.domain.DriverProfile;
import com.example.goride.driver.domain.VehicleType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DriverProfileResponse(
        Long id,
        Long userId,
        String licenseNumber,
        LocalDate licenseExpiry,
        String idCardNumber,
        String portraitUrl,
        String vehiclePlate,
        VehicleType vehicleType,
        String vehicleBrand,
        String vehicleModel,
        String vehicleColor,
        Short vehicleYear,
        ApprovalStatus approvalStatus,
        boolean online,
        BigDecimal averageRating,
        int totalRatings,
        int totalTrips
) {
    public static DriverProfileResponse from(DriverProfile profile) {
        return new DriverProfileResponse(
                profile.getId(),
                profile.getUser().getId(),
                profile.getLicenseNumber(),
                profile.getLicenseExpiry(),
                profile.getIdCardNumber(),
                profile.getPortraitUrl(),
                profile.getVehiclePlate(),
                profile.getVehicleType(),
                profile.getVehicleBrand(),
                profile.getVehicleModel(),
                profile.getVehicleColor(),
                profile.getVehicleYear(),
                profile.getApprovalStatus(),
                profile.isOnline(),
                profile.getAverageRating(),
                profile.getTotalRatings(),
                profile.getTotalTrips()
        );
    }
}
