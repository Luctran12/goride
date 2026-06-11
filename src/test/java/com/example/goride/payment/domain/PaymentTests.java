package com.example.goride.payment.domain;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.booking.domain.PricingConfig;
import com.example.goride.booking.domain.Trip;
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

class PaymentTests {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Test
    void createPendingUsesCompletedTripFareAndMethod() {
        Trip trip = completedTrip();

        Payment payment = Payment.createPending(trip);

        assertThat(payment.getTrip()).isSameAs(trip);
        assertThat(payment.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(20000));
        assertThat(payment.getMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getProvider()).isNull();
        assertThat(payment.getTransactionRef()).isNull();
        assertThat(payment.getPaidAt()).isNull();
    }

    @Test
    void createPendingRejectsTripBeforeCompleted() {
        Trip trip = sampleTrip();

        assertThatThrownBy(() -> Payment.createPending(trip))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Payment can only be created for completed trip");
    }

    @Test
    void markCompletedMovesPendingPaymentToCompleted() {
        Payment payment = Payment.createPending(completedTrip());

        payment.markCompleted();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getPaidAt()).isNotNull();
    }

    @Test
    void markCompletedRejectsNonPendingPayment() {
        Payment payment = Payment.createPending(completedTrip());
        payment.markCompleted();

        assertThatThrownBy(payment::markCompleted)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Payment can only be completed from pending status");
    }

    @Test
    void markCompletedByProviderStoresProviderReference() {
        Payment payment = Payment.createPending(completedTrip());

        payment.markCompletedByProvider(" momo ", " txn-123 ");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getProvider()).isEqualTo("momo");
        assertThat(payment.getTransactionRef()).isEqualTo("txn-123");
        assertThat(payment.getPaidAt()).isNotNull();
    }

    @Test
    void markCompletedByProviderIsIdempotentForSameReference() {
        Payment payment = Payment.createPending(completedTrip());
        payment.markCompletedByProvider("momo", "txn-123");
        Instant firstPaidAt = payment.getPaidAt();

        payment.markCompletedByProvider("momo", "txn-123");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getPaidAt()).isEqualTo(firstPaidAt);
    }

    @Test
    void markCompletedByProviderRejectsDifferentReferenceAfterCompleted() {
        Payment payment = Payment.createPending(completedTrip());
        payment.markCompletedByProvider("momo", "txn-123");

        assertThatThrownBy(() -> payment.markCompletedByProvider("momo", "txn-999"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Payment provider callback does not match existing provider reference");
    }

    @Test
    void markFailedByProviderStoresProviderReference() {
        Payment payment = Payment.createPending(completedTrip());

        payment.markFailedByProvider("vnpay", "txn-456");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getProvider()).isEqualTo("vnpay");
        assertThat(payment.getTransactionRef()).isEqualTo("txn-456");
        assertThat(payment.getPaidAt()).isNull();
    }

    @Test
    void providerReferenceRejectsBlankValues() {
        Payment payment = Payment.createPending(completedTrip());

        assertThatThrownBy(() -> payment.markCompletedByProvider(" ", "txn-123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("provider must not be blank");
    }

    private Trip completedTrip() {
        Trip trip = sampleTrip();
        trip.accept(driver());
        trip.markArrived();
        trip.startTrip();
        trip.complete(BigDecimal.valueOf(20000), BigDecimal.ONE, 20);
        return trip;
    }

    private Trip sampleTrip() {
        PricingConfig pricingConfig = PricingConfig.create(
                VehicleType.MOTORBIKE,
                BigDecimal.valueOf(10000),
                BigDecimal.valueOf(4000),
                BigDecimal.valueOf(300),
                BigDecimal.valueOf(15000),
                BigDecimal.ONE,
                Instant.parse("2026-01-01T00:00:00Z")
        );
        return Trip.create(
                passenger(),
                VehicleType.MOTORBIKE,
                PaymentMethod.CASH,
                "Ben Thanh Market",
                point(106.7000, 10.7700),
                "Tan Son Nhat Airport",
                point(106.6650, 10.8130),
                BigDecimal.valueOf(4.2),
                18,
                BigDecimal.valueOf(32000),
                pricingConfig
        );
    }

    private User passenger() {
        return User.create("Passenger", "0900000000", null, "hash", Set.of(UserRole.PASSENGER));
    }

    private User driver() {
        return User.create("Driver", "0900000001", null, "hash", Set.of(UserRole.DRIVER));
    }

    private static org.locationtech.jts.geom.Point point(double longitude, double latitude) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
    }
}
