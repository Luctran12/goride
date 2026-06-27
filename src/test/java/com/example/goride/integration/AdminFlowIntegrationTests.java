package com.example.goride.integration;

import com.example.goride.user.domain.User;
import com.example.goride.user.domain.UserRole;
import com.example.goride.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminFlowIntegrationTests extends PostgresRedisIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void adminApprovesDriverManagesPricingAndReadsDashboard() throws Exception {
        AuthUser admin = seedAndLoginAdmin("0909000201", "integration.admin@example.com");
        AuthUser driver = register(
                "Integration Admin Driver",
                "0909000202",
                "integration.admin.driver@example.com",
                "DRIVER"
        );
        AuthUser passenger = register(
                "Integration Admin Passenger",
                "0909000203",
                "integration.admin.passenger@example.com",
                "PASSENGER"
        );

        mockMvc.perform(get("/api/v1/admin/dashboard")
                        .header("Authorization", bearer(passenger.accessToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        createDriverProfile(driver.accessToken(), "0202");

        mockMvc.perform(get("/api/v1/admin/drivers/pending?page=1&size=10")
                        .header("Authorization", bearer(admin.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pagination.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].userId").value(driver.userId()))
                .andExpect(jsonPath("$.data.items[0].approvalStatus").value("PENDING"));

        mockMvc.perform(patch("/api/v1/admin/drivers/{driverId}/approval", driver.userId())
                        .header("Authorization", bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approvalStatus": "APPROVED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(driver.userId()))
                .andExpect(jsonPath("$.data.approvalStatus").value("APPROVED"));

        JsonNode pricing = responseBody(mockMvc.perform(post("/api/v1/admin/pricing")
                        .header("Authorization", bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "vehicleType": "CAR_4_SEAT",
                                  "baseFare": 15000,
                                  "perKmRate": 12000,
                                  "perMinuteRate": 2000,
                                  "minimumFare": 50000,
                                  "surgeMultiplier": 1.0,
                                  "effectiveFrom": "2026-06-25T00:00:00Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.vehicleType").value("CAR_4_SEAT"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andReturn());
        long pricingConfigId = pricing.at("/data/id").asLong();
        assertThat(pricingConfigId).isPositive();

        mockMvc.perform(get("/api/v1/admin/pricing")
                        .header("Authorization", bearer(admin.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == %d)].vehicleType".formatted(pricingConfigId))
                        .value("CAR_4_SEAT"));

        mockMvc.perform(patch("/api/v1/admin/pricing/{pricingConfigId}/deactivate", pricingConfigId)
                        .header("Authorization", bearer(admin.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(pricingConfigId))
                .andExpect(jsonPath("$.data.active").value(false));

        mockMvc.perform(get("/api/v1/admin/trips?page=1&size=5")
                        .header("Authorization", bearer(admin.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.pagination.page").value(1));

        mockMvc.perform(get("/api/v1/admin/dashboard")
                        .header("Authorization", bearer(admin.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUsers").value(3))
                .andExpect(jsonPath("$.data.totalDrivers").value(1))
                .andExpect(jsonPath("$.data.approvedDrivers").value(1))
                .andExpect(jsonPath("$.data.pendingDrivers").value(0));
    }

    private AuthUser register(String fullName, String phone, String email, String role) throws Exception {
        JsonNode response = responseBody(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", fullName,
                                "phone", phone,
                                "email", email,
                                "password", "password123",
                                "roles", List.of(role)
                        ))))
                .andExpect(status().isCreated())
                .andReturn());
        return new AuthUser(
                response.at("/data/userId").asLong(),
                response.at("/data/accessToken").asText()
        );
    }

    private AuthUser seedAndLoginAdmin(String phone, String email) throws Exception {
        User admin = userRepository.save(User.create(
                "Integration Admin",
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
                                  "licenseNumber": "ADMIN-GPLX-%s",
                                  "licenseExpiry": "2030-12-31",
                                  "idCardNumber": "ADMIN-ID-%s",
                                  "portraitUrl": "https://example.com/admin-driver.jpg",
                                  "vehiclePlate": "59-ADMIN-%s",
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

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private JsonNode responseBody(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private record AuthUser(long userId, String accessToken) {
    }
}