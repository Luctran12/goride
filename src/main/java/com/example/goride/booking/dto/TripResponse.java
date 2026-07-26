package com.example.goride.booking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.domain.TripStatus;
import com.example.goride.driver.domain.VehicleType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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
        String cancelReason,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        List<TripStatusHistoryResponse> statusHistory
) {
    public static TripResponse from(Trip trip) {
        return from(trip, null);
    }

    public static TripResponse from(Trip trip, List<TripStatusHistoryResponse> statusHistory) {
        Long driverId = trip.getDriver() == null ? null : trip.getDriver().getId();
        List<TripStatusHistoryResponse> detailStatusHistory = statusHistory == null
                ? null
                : List.copyOf(statusHistory);
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
                trip.getCancelReason(),
                detailStatusHistory
        );
    }
}
