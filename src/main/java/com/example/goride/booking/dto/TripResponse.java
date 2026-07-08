package com.example.goride.booking.dto;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.domain.TripStatus;
import com.example.goride.driver.domain.VehicleType;

import java.math.BigDecimal;
import java.time.Instant;

public record TripResponse(
        Long id,
        Long passengerId,
        Long driverId,
        TripStatus status,
        VehicleType vehicleType,
        PaymentMethod paymentMethod,
        BookingLocationResponse pickup,
        BookingLocationResponse dropoff,
        BigDecimal estimatedDistanceKm,
        int estimatedDurationMin,
        BigDecimal estimatedFare,
        BigDecimal finalFare,
        BigDecimal fareSurgeMultiplier,
        Instant requestedAt,
        Instant scheduledPickupTime,
        Instant acceptedAt,
        Instant arrivedAt,
        Instant startedAt,
        Instant completedAt,
        Instant cancelledAt,
        String cancelReason
) {
    public static TripResponse from(Trip trip) {
        Long driverId = trip.getDriver() == null ? null : trip.getDriver().getId();
        return new TripResponse(
                trip.getId(),
                trip.getPassenger().getId(),
                driverId,
                trip.getStatus(),
                trip.getVehicleType(),
                trip.getPaymentMethod(),
                BookingLocationResponse.of(trip.getPickupLocation(), trip.getPickupAddress()),
                BookingLocationResponse.of(trip.getDropoffLocation(), trip.getDropoffAddress()),
                trip.getEstimatedDistanceKm(),
                trip.getEstimatedDurationMin(),
                trip.getEstimatedFare(),
                trip.getFinalFare(),
                trip.getFareSurgeMultiplier(),
                trip.getRequestedAt(),
                trip.getScheduledPickupTime(),
                trip.getAcceptedAt(),
                trip.getArrivedAt(),
                trip.getStartedAt(),
                trip.getCompletedAt(),
                trip.getCancelledAt(),
                trip.getCancelReason()
        );
    }
}
