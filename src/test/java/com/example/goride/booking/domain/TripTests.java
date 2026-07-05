package com.example.goride.booking.domain;

import com.example.goride.driver.domain.VehicleType;
import com.example.goride.user.domain.User;
import com.example.goride.user.domain.UserRole;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TripTests {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Test
    void createStartsSearchingWithCashPayment() {
        Trip trip = sampleTrip();

        assertThat(trip.getStatus()).isEqualTo(TripStatus.SEARCHING);
        assertThat(trip.getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(trip.getPickupAddress()).isEqualTo("Ben Thanh Market");
        assertThat(trip.getDriver()).isNull();
        assertThat(trip.getRequestedAt()).isNotNull();
    }

    @Test
    void createScheduledStartsScheduledAndDispatchesToSearching() {
        Instant scheduledPickupTime = Instant.parse("2026-07-04T10:30:00Z");
        Trip trip = Trip.createScheduled(
                samplePassenger(),
                VehicleType.MOTORBIKE,
                PaymentMethod.CASH,
                "Pickup",
                point(106.7000, 10.7700),
                "Dropoff",
                point(106.7100, 10.7800),
                BigDecimal.valueOf(3.2),
                12,
                BigDecimal.valueOf(25000),
                samplePricingConfig(VehicleType.MOTORBIKE),
                scheduledPickupTime
        );

        assertThat(trip.getStatus()).isEqualTo(TripStatus.SCHEDULED);
        assertThat(trip.getScheduledPickupTime()).isEqualTo(scheduledPickupTime);

        trip.dispatchScheduled();

        assertThat(trip.getStatus()).isEqualTo(TripStatus.SEARCHING);
    }
    @Test
    void createRequiresPassengerRole() {
        User driverOnly = User.create("Driver", "0900000001", null, "hash", Set.of(UserRole.DRIVER));

        assertThatThrownBy(() -> Trip.create(
                driverOnly,
                VehicleType.MOTORBIKE,
                PaymentMethod.CASH,
                "Pickup",
                point(106.7000, 10.7700),
                "Dropoff",
                point(106.7100, 10.7800),
                BigDecimal.valueOf(3.2),
                12,
                BigDecimal.valueOf(25000),
                samplePricingConfig(VehicleType.MOTORBIKE)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Passenger user must have PASSENGER role");
    }

    @Test
    void lifecycleMovesThroughHappyPath() {
        Trip trip = sampleTrip();

        trip.accept(sampleDriver());
        trip.markArrived();
        trip.startTrip();
        trip.complete(BigDecimal.valueOf(30000), BigDecimal.valueOf(4.1), 16);

        assertThat(trip.getStatus()).isEqualTo(TripStatus.COMPLETED);
        assertThat(trip.getDriver()).isNotNull();
        assertThat(trip.getAcceptedAt()).isNotNull();
        assertThat(trip.getArrivedAt()).isNotNull();
        assertThat(trip.getStartedAt()).isNotNull();
        assertThat(trip.getCompletedAt()).isNotNull();
        assertThat(trip.getFinalFare()).isEqualByComparingTo(BigDecimal.valueOf(30000));
    }

    @Test
    void cannotStartBeforeDriverArrival() {
        Trip trip = sampleTrip();
        trip.accept(sampleDriver());

        assertThatThrownBy(trip::startTrip)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Trip can only be started after driver arrival");
    }

    @Test
    void cancelStoresReasonAndPreventsFurtherAcceptance() {
        Trip trip = sampleTrip();

        trip.cancel(" Passenger changed plan ");

        assertThat(trip.getStatus()).isEqualTo(TripStatus.CANCELLED);
        assertThat(trip.getCancelReason()).isEqualTo("Passenger changed plan");
        assertThat(trip.getCancelledAt()).isNotNull();
        assertThatThrownBy(() -> trip.accept(sampleDriver()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Trip can only be accepted while searching");
    }

    @Test
    void noDriverCanOnlyBeSetWhileSearching() {
        Trip trip = sampleTrip();
        trip.accept(sampleDriver());

        assertThatThrownBy(trip::markNoDriver)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Trip can only be marked no driver while searching");
    }

    @Test
    void statusHistoryRecordsTransitionMetadata() {
        Trip trip = sampleTrip();
        User driver = sampleDriver();

        TripStatusHistory history = TripStatusHistory.record(
                trip,
                TripStatus.SEARCHING,
                TripStatus.ACCEPTED,
                driver,
                " Driver accepted "
        );

        assertThat(history.getTrip()).isSameAs(trip);
        assertThat(history.getFromStatus()).isEqualTo(TripStatus.SEARCHING);
        assertThat(history.getToStatus()).isEqualTo(TripStatus.ACCEPTED);
        assertThat(history.getChangedBy()).isSameAs(driver);
        assertThat(history.getNote()).isEqualTo("Driver accepted");
        assertThat(history.getChangedAt()).isNotNull();
    }

    private Trip sampleTrip() {
        return Trip.create(
                samplePassenger(),
                VehicleType.MOTORBIKE,
                null,
                " Ben Thanh Market ",
                point(106.7000, 10.7700),
                "Tan Son Nhat Airport",
                point(106.6650, 10.8130),
                BigDecimal.valueOf(4.2),
                18,
                BigDecimal.valueOf(32000),
                samplePricingConfig(VehicleType.MOTORBIKE)
        );
    }

    private PricingConfig samplePricingConfig(VehicleType vehicleType) {
        return PricingConfig.create(
                vehicleType,
                BigDecimal.valueOf(10000),
                BigDecimal.valueOf(4000),
                BigDecimal.valueOf(300),
                BigDecimal.valueOf(15000),
                BigDecimal.ONE,
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }

    private User samplePassenger() {
        return User.create("Passenger", "0900000000", null, "hash", Set.of(UserRole.PASSENGER));
    }

    private User sampleDriver() {
        return User.create("Driver", "0900000001", null, "hash", Set.of(UserRole.DRIVER));
    }

    private static org.locationtech.jts.geom.Point point(double longitude, double latitude) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
    }
}
