package com.example.goride.chat.domain;

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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TripMessageTests {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final UUID CLIENT_MESSAGE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void createNormalizesMessageBody() {
        Trip trip = sampleTrip();
        User passenger = trip.getPassenger();

        TripMessage message = TripMessage.create(
                trip,
                passenger,
                TripMessageSenderRole.PASSENGER,
                CLIENT_MESSAGE_ID,
                "  Hello driver  "
        );

        assertThat(message.getTrip()).isSameAs(trip);
        assertThat(message.getSender()).isSameAs(passenger);
        assertThat(message.getSenderRole()).isEqualTo(TripMessageSenderRole.PASSENGER);
        assertThat(message.getBody()).isEqualTo("Hello driver");
        assertThat(message.getSentAt()).isNotNull();
    }

    @Test
    void rejectsBlankBody() {
        Trip trip = sampleTrip();

        assertThatThrownBy(() -> TripMessage.create(
                trip,
                trip.getPassenger(),
                TripMessageSenderRole.PASSENGER,
                CLIENT_MESSAGE_ID,
                "   "
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("body must not be blank");
    }

    @Test
    void rejectsTooLongBody() {
        Trip trip = sampleTrip();
        String body = "a".repeat(1001);

        assertThatThrownBy(() -> TripMessage.create(
                trip,
                trip.getPassenger(),
                TripMessageSenderRole.PASSENGER,
                CLIENT_MESSAGE_ID,
                body
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("body must not exceed 1000 characters");
    }

    private Trip sampleTrip() {
        return Trip.create(
                User.create("Passenger", "0900000000", null, "hash", Set.of(UserRole.PASSENGER)),
                VehicleType.MOTORBIKE,
                PaymentMethod.CASH,
                "Pickup",
                point(106.7000, 10.7700),
                "Dropoff",
                point(106.7100, 10.7800),
                BigDecimal.valueOf(4.2),
                18,
                BigDecimal.valueOf(32000),
                PricingConfig.create(
                        VehicleType.MOTORBIKE,
                        BigDecimal.valueOf(10000),
                        BigDecimal.valueOf(4000),
                        BigDecimal.valueOf(300),
                        BigDecimal.valueOf(15000),
                        BigDecimal.ONE,
                        Instant.parse("2026-01-01T00:00:00Z")
                )
        );
    }

    private static org.locationtech.jts.geom.Point point(double longitude, double latitude) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
    }
}
