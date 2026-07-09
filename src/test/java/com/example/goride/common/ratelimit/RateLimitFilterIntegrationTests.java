package com.example.goride.common.ratelimit;

import com.example.goride.booking.repository.PricingConfigRepository;
import com.example.goride.booking.repository.SurgePricingRuleRepository;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.booking.repository.TripStatusHistoryRepository;
import com.example.goride.chat.repository.TripMessageRepository;
import com.example.goride.driver.repository.DriverProfileRepository;
import com.example.goride.notification.repository.NotificationRepository;
import com.example.goride.payment.repository.PaymentRepository;
import com.example.goride.payment.repository.PaymentSandboxUatResultRepository;
import com.example.goride.rating.repository.RatingRepository;
import com.example.goride.tracking.repository.TripLocationHistoryRepository;
import com.example.goride.user.repository.UserRepository;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "management.endpoint.health.group.readiness.include=readinessState",
        "app.security.rate-limit.enabled=true",
        "app.security.rate-limit.capacity=2",
        "app.security.rate-limit.refill-tokens=2",
        "app.security.rate-limit.refill-period-seconds=60",
        "app.security.rate-limit.excluded-paths=/actuator/**"
})
@AutoConfigureMockMvc
class RateLimitFilterIntegrationTests {
    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private DriverProfileRepository driverProfileRepository;

    @MockitoBean
    private PricingConfigRepository pricingConfigRepository;

    @MockitoBean
    private SurgePricingRuleRepository surgePricingRuleRepository;

    @MockitoBean
    private TripRepository tripRepository;

    @MockitoBean
    private TripStatusHistoryRepository tripStatusHistoryRepository;

    @MockitoBean
    private TripLocationHistoryRepository tripLocationHistoryRepository;

    @MockitoBean
    private PaymentRepository paymentRepository;

    @MockitoBean
    private PaymentSandboxUatResultRepository paymentSandboxUatResultRepository;

    @MockitoBean
    private RatingRepository ratingRepository;

    @MockitoBean
    private NotificationRepository notificationRepository;

    @MockitoBean
    private TripMessageRepository tripMessageRepository;

    @Resource
    private MockMvc mockMvc;

    @Test
    void returnsRateLimitErrorAfterClientConsumesBucket() throws Exception {
        String body = """
                {
                  "phone": "0900000000",
                  "password": "wrong-password"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(RateLimitFilter.RATE_LIMIT_LIMIT_HEADER, "2"))
                .andExpect(header().string(RateLimitFilter.RATE_LIMIT_REMAINING_HEADER, "1"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(RateLimitFilter.RATE_LIMIT_REMAINING_HEADER, "0"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
                .andExpect(header().string(RateLimitFilter.RATE_LIMIT_LIMIT_HEADER, "2"))
                .andExpect(header().string(RateLimitFilter.RATE_LIMIT_REMAINING_HEADER, "0"))
                .andExpect(header().exists(RateLimitFilter.RATE_LIMIT_RESET_HEADER))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.error.details.retryAfterSeconds").value(60));
    }

    @Test
    void excludesActuatorEndpointsFromRateLimit() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(RateLimitFilter.RATE_LIMIT_LIMIT_HEADER));

        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }
}
