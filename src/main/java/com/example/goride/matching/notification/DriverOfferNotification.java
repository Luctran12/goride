package com.example.goride.matching.notification;

import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.event.BookingCreatedEvent;
import com.example.goride.driver.domain.VehicleType;
import com.example.goride.matching.domain.DriverOffer;
import org.locationtech.jts.geom.Point;

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

    public static DriverOfferNotification from(Trip trip, DriverOffer offer) {
        Point pickupLocation = trip.getPickupLocation();
        Point dropoffLocation = trip.getDropoffLocation();
        return new DriverOfferNotification(
                trip.getId(),
                trip.getPassenger().getId(),
                trip.getVehicleType(),
                BigDecimal.valueOf(pickupLocation.getY()),
                BigDecimal.valueOf(pickupLocation.getX()),
                BigDecimal.valueOf(dropoffLocation.getY()),
                BigDecimal.valueOf(dropoffLocation.getX()),
                trip.getEstimatedFare(),
                offer.candidate().distanceMeters(),
                offer.offerExpiresAt()
        );
    }
}
