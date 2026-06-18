package com.example.goride.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTests extends PostgresRedisIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registerAccessRefreshLogoutAndRejectDuplicatePhone() throws Exception {
        mockMvc.perform(get("/ws/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.websocket").value(true));

        mockMvc.perform(get("/api/v1/notifications")
                        .header("X-Request-Id", "auth-missing-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", "auth-missing-token"))
                .andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"))
                .andExpect(jsonPath("$.requestId").value("auth-missing-token"));

        JsonNode registered = responseBody(mockMvc.perform(post("/api/v1/auth/register")
                        .header("X-Request-Id", "auth-register-integration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Integration Passenger",
                                  "phone": "0909000001",
                                  "email": "integration.passenger@example.com",
                                  "password": "password123",
                                  "roles": ["PASSENGER"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Request-Id", "auth-register-integration"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").isNumber())
                .andExpect(jsonPath("$.data.roles[0]").value("PASSENGER"))
                .andReturn());

        String accessToken = registered.at("/data/accessToken").asText();
        String initialRefreshToken = registered.at("/data/refreshToken").asText();
        assertThat(accessToken).isNotBlank();
        assertThat(initialRefreshToken).isNotBlank();

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Request-Id", "auth-login-integration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phone": "0909000001",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "auth-login-integration"))
                .andExpect(jsonPath("$.data.userId").value(registered.at("/data/userId").asLong()))
                .andExpect(jsonPath("$.data.roles[0]").value("PASSENGER"));

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray());

        JsonNode refreshed = responseBody(mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenRequest(initialRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn());
        String rotatedRefreshToken = refreshed.at("/data/refreshToken").asText();
        assertThat(rotatedRefreshToken).isNotBlank().isNotEqualTo(initialRefreshToken);

        assertRefreshTokenExpired(initialRefreshToken);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenRequest(rotatedRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        assertRefreshTokenExpired(rotatedRefreshToken);

        mockMvc.perform(post("/api/v1/auth/register")
                        .header("X-Request-Id", "auth-duplicate-phone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Duplicate Passenger",
                                  "phone": "0909000001",
                                  "password": "password123",
                                  "roles": ["PASSENGER"]
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(header().string("X-Request-Id", "auth-duplicate-phone"))
                .andExpect(jsonPath("$.error.code").value("PHONE_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.requestId").value("auth-duplicate-phone"));
    }

    private void assertRefreshTokenExpired(String refreshToken) throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenRequest(refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_EXPIRED"));
    }

    private String tokenRequest(String refreshToken) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of("refreshToken", refreshToken));
    }

    private JsonNode responseBody(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
