package com.example.goride.booking.event;

import com.example.goride.booking.domain.Trip;
import com.example.goride.driver.domain.VehicleType;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;

public record BookingCreatedEvent(
        Long tripId,
        Long passengerId,
        VehicleType vehicleType,
        BigDecimal pickupLatitude,
        BigDecimal pickupLongitude,
        BigDecimal dropoffLatitude,
        BigDecimal dropoffLongitude,
        BigDecimal estimatedFare
) {
    public static BookingCreatedEvent from(Trip trip) {
        Point pickup = trip.getPickupLocation();
        Point dropoff = trip.getDropoffLocation();
        return new BookingCreatedEvent(
                trip.getId(),
                trip.getPassenger().getId(),
                trip.getVehicleType(),
                BigDecimal.valueOf(pickup.getY()),
                BigDecimal.valueOf(pickup.getX()),
                BigDecimal.valueOf(dropoff.getY()),
                BigDecimal.valueOf(dropoff.getX()),
                trip.getEstimatedFare()
        );
    }
}
