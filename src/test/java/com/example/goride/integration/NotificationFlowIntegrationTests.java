package com.example.goride.integration;

import com.example.goride.notification.domain.NotificationType;
import com.example.goride.notification.dto.UserNotification;
import com.example.goride.notification.service.FcmDeviceTokenStore;
import com.example.goride.notification.service.TripRealtimeNotifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationFlowIntegrationTests extends PostgresRedisIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TripRealtimeNotifier tripRealtimeNotifier;

    @Autowired
    private FcmDeviceTokenStore fcmDeviceTokenStore;

    @Test
    void userManagesFcmTokenAndReadsInboxNotification() throws Exception {
        JsonNode registered = registerPassenger(
                "Integration Notification Passenger",
                "0909000101",
                "integration.notification@example.com"
        );
        long userId = registered.at("/data/userId").asLong();
        String accessToken = registered.at("/data/accessToken").asText();

        mockMvc.perform(put("/api/v1/notifications/fcm-token")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": " integration-fcm-token-101 "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.registered").value(true));
        assertThat(fcmDeviceTokenStore.findToken(userId)).contains("integration-fcm-token-101");

        tripRealtimeNotifier.notifyUser(userId, new UserNotification(
                NotificationType.TRIP_ACCEPTED,
                "Trip accepted",
                "Your driver is on the way",
                Map.of("tripId", 99L, "driverId", 20L, "status", "ACCEPTED"),
                Instant.parse("2026-05-20T04:00:00Z")
        ));

        JsonNode inbox = responseBody(mockMvc.perform(get("/api/v1/notifications?page=1&size=10")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pagination.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].type").value("TRIP_ACCEPTED"))
                .andExpect(jsonPath("$.data.items[0].read").value(false))
                .andExpect(jsonPath("$.data.items[0].data.tripId").value(99))
                .andReturn());
        long notificationId = inbox.at("/data/items/0/notificationId").asLong();

        mockMvc.perform(patch("/api/v1/notifications/{notificationId}/read", notificationId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notificationId").value(notificationId))
                .andExpect(jsonPath("$.data.read").value(true))
                .andExpect(jsonPath("$.data.readAt").isNotEmpty());

        mockMvc.perform(delete("/api/v1/notifications/fcm-token")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.registered").value(false));
        assertThat(fcmDeviceTokenStore.findToken(userId)).isEmpty();
    }

    private JsonNode registerPassenger(String fullName, String phone, String email) throws Exception {
        return responseBody(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", fullName,
                                "phone", phone,
                                "email", email,
                                "password", "password123",
                                "roles", List.of("PASSENGER")
                        ))))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private JsonNode responseBody(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }
}