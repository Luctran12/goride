package com.example.goride.booking.dto;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.booking.domain.PricingConfig;
import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.domain.TripStatus;
import com.example.goride.driver.domain.VehicleType;
import com.example.goride.driver.dto.AssignedDriverResponse;
import com.example.goride.user.domain.User;
import com.example.goride.user.domain.UserRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TripResponseJsonTests {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void omitsEmptyStatusHistoryOutsideDetailResponses() throws Exception {
        JsonNode json = objectMapper.valueToTree(TripResponse.from(sampleTrip()));

        assertThat(json.has("statusHistory")).isFalse();
    }

    @Test
    void serializesStatusHistoryForDetailResponses() throws Exception {
        TripStatusHistoryResponse history = new TripStatusHistoryResponse(
                1L,
                null,
                TripStatus.SEARCHING,
                10L,
                "Booking created",
                Instant.parse("2026-05-18T08:00:00Z")
        );

        JsonNode json = objectMapper.valueToTree(TripResponse.from(sampleTrip(), List.of(history)));

        assertThat(json.path("statusHistory").isArray()).isTrue();
        assertThat(json.path("statusHistory").get(0).path("toStatus").asText()).isEqualTo("SEARCHING");
        assertThat(json.path("statusHistory").get(0).path("changedByUserId").asLong()).isEqualTo(10L);
    }

    @Test
    void serializesEmptyStatusHistoryForDetailResponses() throws Exception {
        JsonNode json = objectMapper.valueToTree(TripResponse.from(sampleTrip(), List.of()));

        assertThat(json.path("statusHistory").isArray()).isTrue();
        assertThat(json.path("statusHistory")).isEmpty();
    }

    @Test
    void serializesSafeAssignedDriverDetails() {
        AssignedDriverResponse driver = new AssignedDriverResponse(
                20L,
                "Nguyen Van Driver",
                "https://example.com/driver.jpg",
                BigDecimal.valueOf(4.9),
                120,
                350,
                "59-A1 123.45",
                VehicleType.MOTORBIKE,
                "Honda",
                "Wave",
                "Blue",
                (short) 2024
        );

        JsonNode json = objectMapper.valueToTree(TripResponse.from(sampleTrip(), driver));

        assertThat(json.at("/driver/id").asLong()).isEqualTo(20L);
        assertThat(json.at("/driver/fullName").asText()).isEqualTo("Nguyen Van Driver");
        assertThat(json.at("/driver/vehiclePlate").asText()).isEqualTo("59-A1 123.45");
        assertThat(json.at("/driver/vehicleBrand").asText()).isEqualTo("Honda");
        assertThat(json.at("/driver/averageRating").decimalValue()).isEqualByComparingTo("4.9");
        assertThat(json.at("/driver/licenseNumber").isMissingNode()).isTrue();
        assertThat(json.at("/driver/idCardNumber").isMissingNode()).isTrue();
    }

    private Trip sampleTrip() {
        User passenger = User.create(
                "Passenger",
                "0900000000",
                null,
                "hash",
                Set.of(UserRole.PASSENGER)
        );
        ReflectionTestUtils.setField(passenger, "id", 10L);
        PricingConfig pricingConfig = PricingConfig.create(
                VehicleType.MOTORBIKE,
                BigDecimal.valueOf(10000),
                BigDecimal.valueOf(4000),
                BigDecimal.valueOf(300),
                BigDecimal.valueOf(15000),
                BigDecimal.ONE,
                Instant.parse("2026-01-01T00:00:00Z")
        );
        Trip trip = Trip.create(
                passenger,
                VehicleType.MOTORBIKE,
                PaymentMethod.CASH,
                "123 Le Loi, Q1",
                GEOMETRY_FACTORY.createPoint(new Coordinate(106.7009, 10.7769)),
                "456 CMT8, Q3",
                GEOMETRY_FACTORY.createPoint(new Coordinate(106.6800, 10.7850)),
                BigDecimal.valueOf(5.5),
                20,
                BigDecimal.valueOf(38000),
                pricingConfig
        );
        ReflectionTestUtils.setField(trip, "id", 99L);
        return trip;
    }
}
