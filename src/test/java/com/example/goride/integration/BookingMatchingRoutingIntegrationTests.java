package com.example.goride.integration;

import com.example.goride.tracking.dto.DriverLocationUpdateRequest;
import com.example.goride.tracking.service.TripLocationTrackingService;
import com.example.goride.user.domain.User;
import com.example.goride.user.domain.UserRole;
import com.example.goride.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookingMatchingRoutingIntegrationTests extends PostgresRedisIntegrationTest {
    private static final HttpServer ROUTING_SERVER = startRoutingServer();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TripLocationTrackingService tripLocationTrackingService;

    @DynamicPropertySource
    static void routingProperties(DynamicPropertyRegistry registry) {
        registry.add("app.routing.enabled", () -> "true");
        registry.add("app.routing.base-url", () ->
                "http://127.0.0.1:" + ROUTING_SERVER.getAddress().getPort());
        registry.add("app.routing.fallback-enabled", () -> "false");
    }

    @AfterAll
    static void stopRoutingServer() {
        ROUTING_SERVER.stop(0);
    }

    @Test
    void booksMatchesDriverAndRoutesToPickupThenDropoff() throws Exception {
        AuthUser passenger = register(
                "Flow Passenger",
                "0909100001",
                "flow.passenger@example.com",
                "PASSENGER"
        );
        AuthUser driver = register(
                "Flow Driver",
                "0909100002",
                "flow.driver@example.com",
                "DRIVER"
        );
        AuthUser admin = seedAndLoginAdmin("0909100003", "flow.admin@example.com");

        createDriverProfile(driver.accessToken(), "001");
        approveDriver(admin.accessToken(), driver.userId());
        setDriverOnline(driver.accessToken());

        JsonNode booking = responseBody(mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", bearer(passenger.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pickup": {
                                    "lat": 10.7769,
                                    "lng": 106.7009,
                                    "address": "Ben Thanh Market"
                                  },
                                  "dropoff": {
                                    "lat": 10.8130,
                                    "lng": 106.6650,
                                    "address": "Tan Son Nhat Airport"
                                  },
                                  "vehicleType": "MOTORBIKE",
                                  "paymentMethod": "CASH"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SEARCHING"))
                .andReturn());
        long tripId = booking.at("/data/id").asLong();
        assertThat(tripId).isPositive();

        mockMvc.perform(patch("/api/v1/drivers/trips/{tripId}/respond", tripId)
                        .header("Authorization", bearer(driver.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action": "ACCEPT"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tripId").value(tripId))
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));

        mockMvc.perform(post("/api/v1/drivers/trips/{tripId}/route", tripId)
                        .header("Authorization", bearer(driver.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "latitude": 10.7700,
                                  "longitude": 106.6900
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tripStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.destinationType").value("PICKUP"))
                .andExpect(jsonPath("$.data.destination.lat").value(10.7769))
                .andExpect(jsonPath("$.data.destination.lng").value(106.7009))
                .andExpect(jsonPath("$.data.geometry.type").value("LineString"))
                .andExpect(jsonPath("$.data.geometry.coordinates.length()").value(2))
                .andExpect(jsonPath("$.data.steps[0].maneuverType").value("turn"));

        mockMvc.perform(patch("/api/v1/drivers/trips/{tripId}/status", tripId)
                        .header("Authorization", bearer(driver.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "ARRIVED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARRIVED"));

        mockMvc.perform(post("/api/v1/drivers/trips/{tripId}/route", tripId)
                        .header("Authorization", bearer(driver.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "latitude": 10.7769,
                                  "longitude": 106.7009
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tripStatus").value("ARRIVED"))
                .andExpect(jsonPath("$.data.destinationType").value("DROPOFF"))
                .andExpect(jsonPath("$.data.destination.lat").value(10.813))
                .andExpect(jsonPath("$.data.destination.lng").value(106.665))
                .andExpect(jsonPath("$.data.geometry.type").value("LineString"))
                .andExpect(jsonPath("$.data.geometry.coordinates[1][0]").value(106.665))
                .andExpect(jsonPath("$.data.geometry.coordinates[1][1]").value(10.813));
    }

    @Test
    void completesTripCreatesCashPaymentAndReturnsDriverToHeartbeatQueue() throws Exception {
        AuthUser passenger = register(
                "Payment Flow Passenger",
                "0909200001",
                "payment.flow.passenger@example.com",
                "PASSENGER"
        );
        AuthUser driver = register(
                "Payment Flow Driver",
                "0909200002",
                "payment.flow.driver@example.com",
                "DRIVER"
        );
        AuthUser admin = seedAndLoginAdmin("0909200003", "payment.flow.admin@example.com");

        createDriverProfile(driver.accessToken(), "002");
        approveDriver(admin.accessToken(), driver.userId());
        setDriverOnline(driver.accessToken());

        JsonNode booking = responseBody(mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", bearer(passenger.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pickup": {
                                    "lat": 10.7769,
                                    "lng": 106.7009,
                                    "address": "Ben Thanh Market"
                                  },
                                  "dropoff": {
                                    "lat": 10.8130,
                                    "lng": 106.6650,
                                    "address": "Tan Son Nhat Airport"
                                  },
                                  "vehicleType": "MOTORBIKE",
                                  "paymentMethod": "CASH"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SEARCHING"))
                .andReturn());
        long tripId = booking.at("/data/id").asLong();

        mockMvc.perform(patch("/api/v1/drivers/trips/{tripId}/respond", tripId)
                        .header("Authorization", bearer(driver.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action": "ACCEPT"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));

        mockMvc.perform(patch("/api/v1/drivers/trips/{tripId}/status", tripId)
                        .header("Authorization", bearer(driver.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "ARRIVED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARRIVED"));

        mockMvc.perform(patch("/api/v1/drivers/trips/{tripId}/status", tripId)
                        .header("Authorization", bearer(driver.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "IN_PROGRESS"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

        tripLocationTrackingService.updateDriverLocation(driver.userId(), new DriverLocationUpdateRequest(
                BigDecimal.valueOf(10.7800),
                BigDecimal.valueOf(106.7050),
                BigDecimal.valueOf(45),
                BigDecimal.valueOf(25)
        ));
        tripLocationTrackingService.updateDriverLocation(driver.userId(), new DriverLocationUpdateRequest(
                BigDecimal.valueOf(10.7900),
                BigDecimal.valueOf(106.7100),
                BigDecimal.valueOf(50),
                BigDecimal.valueOf(28)
        ));

        mockMvc.perform(get("/api/v1/tracking/trips/{tripId}/driver-location", tripId)
                        .header("Authorization", bearer(passenger.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tripId").value(tripId))
                .andExpect(jsonPath("$.data.driverId").value(driver.userId()))
                .andExpect(jsonPath("$.data.lat").value(10.79))
                .andExpect(jsonPath("$.data.lng").value(106.71));

        mockMvc.perform(patch("/api/v1/drivers/trips/{tripId}/status", tripId)
                        .header("Authorization", bearer(driver.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "COMPLETED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        JsonNode completedTrip = responseBody(mockMvc.perform(get("/api/v1/bookings/{tripId}", tripId)
                        .header("Authorization", bearer(passenger.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completedAt").isNotEmpty())
                .andReturn());
        BigDecimal finalFare = completedTrip.at("/data/finalFare").decimalValue();
        assertThat(finalFare).isPositive();

        JsonNode pendingPayment = responseBody(mockMvc.perform(get("/api/v1/payments/trips/{tripId}", tripId)
                        .header("Authorization", bearer(passenger.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.method").value("CASH"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn());
        assertThat(pendingPayment.at("/data/amount").decimalValue()).isEqualByComparingTo(finalFare);

        JsonNode checkout = responseBody(mockMvc.perform(get("/api/v1/payments/trips/{tripId}/checkout", tripId)
                        .header("Authorization", bearer(passenger.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checkoutRequired").value(false))
                .andExpect(jsonPath("$.data.checkoutUrl").doesNotExist())
                .andReturn());
        assertThat(checkout.at("/data/amount").decimalValue()).isEqualByComparingTo(finalFare);

        JsonNode confirmation = responseBody(mockMvc.perform(patch("/api/v1/drivers/trips/{tripId}/payment-confirm", tripId)
                        .header("Authorization", bearer(driver.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.paidAt").isNotEmpty())
                .andReturn());
        assertThat(confirmation.at("/data/amount").decimalValue()).isEqualByComparingTo(finalFare);

        mockMvc.perform(get("/api/v1/payments/trips/{tripId}", tripId)
                        .header("Authorization", bearer(passenger.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        mockMvc.perform(post("/api/v1/drivers/me/heartbeat")
                        .header("Authorization", bearer(driver.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lat": 10.7900,
                                  "lng": 106.7100
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.online").value(true));
    }

    private AuthUser register(String fullName, String phone, String email, String role) throws Exception {
        String request = objectMapper.writeValueAsString(java.util.Map.of(
                "fullName", fullName,
                "phone", phone,
                "email", email,
                "password", "password123",
                "roles", java.util.List.of(role)
        ));
        JsonNode response = responseBody(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn());
        return new AuthUser(
                response.at("/data/userId").asLong(),
                response.at("/data/accessToken").asText()
        );
    }

    private AuthUser seedAndLoginAdmin(String phone, String email) throws Exception {
        User admin = userRepository.save(User.create(
                "Flow Admin " + phone.substring(phone.length() - 2),
                phone,
                email,
                passwordEncoder.encode("password123"),
                Set.of(UserRole.ADMIN)
        ));
        JsonNode response = responseBody(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phone": "%s",
                                  "password": "password123"
                                }
                                """.formatted(phone)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0]").value("ADMIN"))
                .andReturn());
        return new AuthUser(admin.getId(), response.at("/data/accessToken").asText());
    }

    private void createDriverProfile(String driverToken, String suffix) throws Exception {
        mockMvc.perform(post("/api/v1/drivers/me/profile")
                        .header("Authorization", bearer(driverToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "licenseNumber": "FLOW-GPLX-%s",
                                  "licenseExpiry": "2030-12-31",
                                  "idCardNumber": "FLOW-ID-%s",
                                  "portraitUrl": "https://example.com/flow-driver.jpg",
                                  "vehiclePlate": "59-FLOW-%s",
                                  "vehicleType": "MOTORBIKE",
                                  "vehicleBrand": "Honda",
                                  "vehicleModel": "Wave",
                                  "vehicleColor": "Blue",
                                  "vehicleYear": 2024
                                }
                                """.formatted(suffix, suffix, suffix)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.approvalStatus").value("PENDING"));
    }

    private void approveDriver(String adminToken, long driverId) throws Exception {
        mockMvc.perform(patch("/api/v1/admin/drivers/{driverId}/approval", driverId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"approvalStatus": "APPROVED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalStatus").value("APPROVED"));
    }

    private void setDriverOnline(String driverToken) throws Exception {
        mockMvc.perform(patch("/api/v1/drivers/me/status")
                        .header("Authorization", bearer(driverToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "online": true,
                                  "lat": 10.7768,
                                  "lng": 106.7008
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.online").value(true));
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private JsonNode responseBody(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private static HttpServer startRoutingServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/route/v1/driving", BookingMatchingRoutingIntegrationTests::handleRoute);
            server.start();
            return server;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void handleRoute(HttpExchange exchange) throws IOException {
        String coordinatePath = exchange.getRequestURI().getPath()
                .substring("/route/v1/driving/".length());
        String[] locations = coordinatePath.split(";");
        String query = exchange.getRequestURI().getRawQuery();
        String body;
        if (query != null && query.contains("geometries=geojson")) {
            body = """
                    {
                      "code": "Ok",
                      "routes": [{
                        "distance": 2300.0,
                        "duration": 480.0,
                        "geometry": {
                          "type": "LineString",
                          "coordinates": [%s, %s]
                        },
                        "legs": [{
                          "steps": [{
                            "distance": 2300.0,
                            "duration": 480.0,
                            "name": "Integration Route",
                            "maneuver": {
                              "type": "turn",
                              "modifier": "straight",
                              "location": %s
                            }
                          }]
                        }]
                      }]
                    }
                    """.formatted(asCoordinate(locations[0]), asCoordinate(locations[1]), asCoordinate(locations[0]));
        } else {
            body = """
                    {"code":"Ok","routes":[{"distance":5200.0,"duration":900.0}]}
                    """;
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String asCoordinate(String longitudeLatitude) {
        String[] parts = longitudeLatitude.split(",");
        return "[" + parts[0] + "," + parts[1] + "]";
    }

    private record AuthUser(long userId, String accessToken) {
    }
}
