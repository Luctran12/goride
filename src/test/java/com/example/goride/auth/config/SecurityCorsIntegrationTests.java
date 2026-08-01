package com.example.goride.auth.config;

import com.example.goride.analytics.repository.DriverSupplySnapshotRepository;
import com.example.goride.analytics.repository.DirectAnalyticsQueryPort;
import com.example.goride.analytics.repository.MatchingOfferEventRepository;
import com.example.goride.analytics.repository.MatchingRunRepository;
import com.example.goride.booking.repository.PricingConfigRepository;
import com.example.goride.booking.repository.SurgePricingRuleRepository;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.booking.repository.TripStatusHistoryRepository;
import com.example.goride.chat.repository.TripMessageRepository;
import com.example.goride.driver.repository.DriverProfileRepository;
import com.example.goride.location.config.ThreeWordLocationProperties;
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
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "management.endpoint.health.group.readiness.include=readinessState")
@AutoConfigureMockMvc
class SecurityCorsIntegrationTests {
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

    @MockitoBean
    private MatchingRunRepository matchingRunRepository;

    @MockitoBean
    private MatchingOfferEventRepository matchingOfferEventRepository;

    @MockitoBean
    private DriverSupplySnapshotRepository driverSupplySnapshotRepository;

    @MockitoBean
    private DirectAnalyticsQueryPort directAnalyticsQueryPort;

    @Resource
    private MockMvc mockMvc;

    @Resource
    private ThreeWordLocationProperties threeWordLocationProperties;

    @Test
    void allowsConfiguredFrontendOriginForPreflightBeforeJwtAuthentication() throws Exception {
        mockMvc.perform(options("/api/v1/notifications")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, GET.name())
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,X-Request-Id"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,PUT,PATCH,DELETE,OPTIONS"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "Authorization, X-Request-Id"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600"));
    }

    @Test
    void exposesRequestIdHeaderForAllowedOriginActualRequests() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        "X-Request-Id, Retry-After, X-RateLimit-Limit, X-RateLimit-Remaining, X-RateLimit-Reset"
                ))
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void exposesSafeTracingRuntimeFlagsWithoutCollectorDetails() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.observability.tracingEnabled").value(false))
                .andExpect(jsonPath("$.observability.otlpExportEnabled").value(false))
                .andExpect(jsonPath("$.observability.endpoint").doesNotExist());
    }

    @Test
    void requiresAuthenticationForThreeWordLocationLookup() throws Exception {
        mockMvc.perform(get("/api/v1/locations/to-words")
                        .param("lat", "10.7769")
                        .param("lng", "106.7009"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"));
    }

    @Test
    void keepsThreeWordLocationProviderDisabledByDefault() {
        assertThat(threeWordLocationProperties.isEnabled()).isFalse();
    }

    @Test
    void rejectsOriginsOutsideAllowlist() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, POST.name()))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
