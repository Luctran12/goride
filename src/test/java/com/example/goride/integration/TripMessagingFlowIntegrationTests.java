package com.example.goride.integration;

import com.example.goride.auth.dto.AuthResponse;
import com.example.goride.auth.dto.RegisterRequest;
import com.example.goride.auth.service.AuthService;
import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.booking.domain.PricingConfig;
import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.repository.PricingConfigRepository;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.driver.domain.VehicleType;
import com.example.goride.user.domain.User;
import com.example.goride.user.domain.UserRole;
import com.example.goride.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TripMessagingFlowIntegrationTests extends PostgresRedisIntegrationTest {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AuthService authService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PricingConfigRepository pricingConfigRepository;
    @Autowired
    private TripRepository tripRepository;

    @Test
    void passengerAndDriverSendSyncAndReadMessagesWithoutDuplicateRetry() throws Exception {
        AuthResponse passengerAuth = register("Chat Passenger", "0908110001", UserRole.PASSENGER);
        AuthResponse driverAuth = register("Chat Driver", "0908110002", UserRole.DRIVER);
        Trip trip = acceptedTrip(passengerAuth.userId(), driverAuth.userId());
        UUID clientMessageId = UUID.fromString("81100000-0000-0000-0000-000000000001");

        JsonNode created = responseBody(mockMvc.perform(post("/api/v1/trips/{tripId}/messages", trip.getId())
                        .header("Authorization", bearer(passengerAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newMessage(clientMessageId, "Waiting at gate A"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.clientMessageId").value(clientMessageId.toString()))
                .andExpect(jsonPath("$.data.body").value("Waiting at gate A"))
                .andReturn());
        long messageId = created.at("/data/id").asLong();

        mockMvc.perform(post("/api/v1/trips/{tripId}/messages", trip.getId())
                        .header("Authorization", bearer(passengerAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newMessage(clientMessageId, "Retry body ignored"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(messageId))
                .andExpect(jsonPath("$.data.body").value("Waiting at gate A"));

        mockMvc.perform(get("/api/v1/trips/{tripId}/messages/sync", trip.getId())
                        .header("Authorization", bearer(driverAuth))
                        .param("afterId", "0")
                        .param("limit", "50"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/trips/{tripId}/messages/sync", trip.getId())
                        .header("Authorization", bearer(driverAuth))
                        .param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(messageId))
                .andExpect(jsonPath("$.data.mode").value("INITIAL"));

        mockMvc.perform(put("/api/v1/trips/{tripId}/messages/read-state", trip.getId())
                        .header("Authorization", bearer(driverAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "lastReadMessageId", messageId
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lastReadMessageId").value(messageId))
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        mockMvc.perform(get("/api/v1/trips/{tripId}/messages/unread-count", trip.getId())
                        .header("Authorization", bearer(driverAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        assertThat(responseBody(mockMvc.perform(get("/api/v1/trips/{tripId}/messages", trip.getId())
                        .header("Authorization", bearer(driverAuth)))
                .andExpect(status().isOk())
                .andReturn()).at("/data/pagination/totalItems").asLong()).isEqualTo(1L);
    }

    private AuthResponse register(String name, String phone, UserRole role) {
        return authService.register(new RegisterRequest(
                name,
                phone,
                null,
                "password123",
                Set.of(role)
        ));
    }

    private Trip acceptedTrip(Long passengerId, Long driverId) {
        User passenger = userRepository.findById(passengerId).orElseThrow();
        User driver = userRepository.findById(driverId).orElseThrow();
        PricingConfig pricing = pricingConfigRepository.save(PricingConfig.create(
                VehicleType.MOTORBIKE,
                BigDecimal.valueOf(10000),
                BigDecimal.valueOf(4000),
                BigDecimal.valueOf(300),
                BigDecimal.valueOf(15000),
                BigDecimal.ONE,
                Instant.parse("2026-01-01T00:00:00Z")
        ));
        Trip trip = Trip.create(
                passenger,
                VehicleType.MOTORBIKE,
                PaymentMethod.CASH,
                "Pickup",
                point(106.7000, 10.7700),
                "Dropoff",
                point(106.7100, 10.7800),
                BigDecimal.valueOf(4.2),
                18,
                BigDecimal.valueOf(32000),
                pricing
        );
        trip.accept(driver);
        return tripRepository.saveAndFlush(trip);
    }

    private java.util.Map<String, Object> newMessage(UUID clientMessageId, String body) {
        return java.util.Map.of("clientMessageId", clientMessageId, "body", body);
    }

    private String bearer(AuthResponse auth) {
        return "Bearer " + auth.accessToken();
    }

    private JsonNode responseBody(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private static org.locationtech.jts.geom.Point point(double longitude, double latitude) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
    }
}
