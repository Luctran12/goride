package com.example.goride.analytics.controller;

import com.example.goride.analytics.service.AnalyticsQueryObservation;
import com.example.goride.analytics.service.DemandForecastQueryService;
import com.example.goride.common.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminDemandForecastController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, AdminDemandForecastControllerTests.MethodSecurity.class})
class AdminDemandForecastControllerTests {
    @MockitoBean
    private DemandForecastQueryService forecastService;

    @MockitoBean
    private AnalyticsQueryObservation queryObservation;

    @jakarta.annotation.Resource
    private MockMvc mockMvc;

    @Test
    void protectsEveryForecastingEndpointWithAdminRole() {
        PreAuthorize authorization = AnnotatedElementUtils.findMergedAnnotation(
                AdminDemandForecastController.class,
                PreAuthorize.class
        );

        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo("hasRole('ADMIN')");
    }

    @Test
    @WithMockUser(roles = "PASSENGER")
    void rejectsNonAdminWithForbiddenEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/admin/analytics/processing/status"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void rejectsMissingAndMalformedForecastFilters() throws Exception {
        mockMvc.perform(get("/api/v1/admin/analytics/forecast/demand")
                        .param("from", "2014-06-01T00:00:00Z")
                        .param("horizonMinutes", "15")
                        .param("cellSizeMeters", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.to").exists());

        mockMvc.perform(get("/api/v1/admin/analytics/forecast/demand")
                        .param("from", "2014-06-01T00:00:00Z")
                        .param("to", "2014-06-01T01:00:00Z")
                        .param("horizonMinutes", "fifteen")
                        .param("cellSizeMeters", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.horizonMinutes").exists());
    }

    @EnableMethodSecurity
    static class MethodSecurity {
    }
}
