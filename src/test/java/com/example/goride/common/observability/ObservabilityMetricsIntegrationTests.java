package com.example.goride.common.observability;

import com.example.goride.booking.repository.PricingConfigRepository;
import com.example.goride.booking.repository.SurgePricingRuleRepository;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.booking.repository.TripStatusHistoryRepository;
import com.example.goride.chat.repository.TripMessageRepository;
import com.example.goride.driver.repository.DriverProfileRepository;
import com.example.goride.notification.repository.NotificationRepository;
import com.example.goride.payment.repository.PaymentRepository;
import com.example.goride.payment.repository.PaymentSandboxE2eSessionRepository;
import com.example.goride.payment.repository.PaymentSandboxUatResultRepository;
import com.example.goride.rating.repository.RatingRepository;
import com.example.goride.servicearea.repository.ServiceAreaRepository;
import com.example.goride.tracking.repository.TripLocationHistoryRepository;
import com.example.goride.user.repository.UserRepository;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "management.endpoint.health.group.readiness.include=readinessState",
        "app.security.rate-limit.enabled=true",
        "app.security.rate-limit.capacity=1",
        "app.security.rate-limit.refill-tokens=1",
        "app.security.rate-limit.refill-period-seconds=60"
})
@AutoConfigureMockMvc
class ObservabilityMetricsIntegrationTests {
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
    private PaymentSandboxE2eSessionRepository paymentSandboxE2eSessionRepository;
    @MockitoBean
    private RatingRepository ratingRepository;

    @MockitoBean
    private ServiceAreaRepository serviceAreaRepository;

    @MockitoBean
    private NotificationRepository notificationRepository;

    @MockitoBean
    private TripMessageRepository tripMessageRepository;

    @Resource
    private MockMvc mockMvc;

    @Test
    void prometheusEndpointIsPublicAndIncludesRateLimitMetrics() throws Exception {
        String body = """
                {
                  "phone": "0900000000",
                  "password": "wrong-password"
                }
                """;

        mockMvc.perform(get("/actuator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.metrics.href").exists())
                .andExpect(jsonPath("$._links.prometheus.href").exists());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("goride_rate_limit_requests_total")))
                .andExpect(content().string(containsString("outcome=\"allowed\"")))
                .andExpect(content().string(containsString("outcome=\"rejected\"")))
                .andExpect(content().string(containsString("goride_rate_limit_buckets")));
    }
}
