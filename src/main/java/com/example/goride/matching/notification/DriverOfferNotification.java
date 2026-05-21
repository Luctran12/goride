package com.example.goride.matching.notification;

import com.example.goride.booking.event.BookingCreatedEvent;
import com.example.goride.driver.domain.VehicleType;
import com.example.goride.matching.domain.DriverOffer;

import java.math.BigDecimal;
import java.time.Instant;

public record DriverOfferNotification(
        Long tripId,
        Long passengerId,
        VehicleType vehicleType,
        BigDecimal pickupLatitude,
        BigDecimal pickupLongitude,
        BigDecimal dropoffLatitude,
        BigDecimal dropoffLongitude,
        BigDecimal estimatedFare,
        long distanceMeters,
        Instant expiresAt
) {
    public static DriverOfferNotification from(BookingCreatedEvent event, DriverOffer offer) {
        return new DriverOfferNotification(
                event.tripId(),
                event.passengerId(),
                event.vehicleType(),
                event.pickupLatitude(),
                event.pickupLongitude(),
                event.dropoffLatitude(),
                event.dropoffLongitude(),
                event.estimatedFare(),
                offer.candidate().distanceMeters(),
                offer.offerExpiresAt()
        );
    }
}
