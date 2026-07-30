package com.example.goride.analytics.controller;

import com.example.goride.analytics.dto.AnalyticsOverviewResponse;
import com.example.goride.analytics.model.AnalyticsSourceVariant;
import com.example.goride.analytics.service.AdminAnalyticsQueryService;
import com.example.goride.analytics.service.AnalyticsQueryObservation;
import com.example.goride.common.error.GlobalExceptionHandler;
import com.example.goride.driver.domain.VehicleType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminAnalyticsControllerTests {
    @Test
    void protectsEveryAnalyticsEndpointWithAdminRole() {
        PreAuthorize authorization = AnnotatedElementUtils.findMergedAnnotation(
                AdminAnalyticsController.class,
                PreAuthorize.class
        );

        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo("hasRole('ADMIN')");
    }

    @Test
    void delegatesNormalizedOverviewRequestToService() {
        AdminAnalyticsQueryService service = mock(AdminAnalyticsQueryService.class);
        AdminAnalyticsController controller = new AdminAnalyticsController(
                service,
                queryObservation()
        );
        OffsetDateTime from = OffsetDateTime.parse("2026-07-01T00:00:00+07:00");
        OffsetDateTime to = OffsetDateTime.parse("2026-07-02T00:00:00+07:00");
        AnalyticsOverviewResponse expected = new AnalyticsOverviewResponse(
                from.toInstant(),
                to.toInstant(),
                "Asia/Ho_Chi_Minh",
                AnalyticsSourceVariant.DIRECT,
                Instant.parse("2026-07-28T03:00:00Z"),
                0,
                0,
                0,
                0,
                0,
                null,
                0,
                BigDecimal.ZERO,
                0,
                0,
                null,
                null,
                null,
                null
        );
        when(service.getOverview(
                from,
                to,
                "Asia/Ho_Chi_Minh",
                VehicleType.MOTORBIKE,
                12L
        )).thenReturn(expected);

        var response = controller.getOverview(
                from,
                to,
                "Asia/Ho_Chi_Minh",
                VehicleType.MOTORBIKE,
                12L
        );

        assertThat(response.data()).isEqualTo(expected);
        verify(service).getOverview(
                from,
                to,
                "Asia/Ho_Chi_Minh",
                VehicleType.MOTORBIKE,
                12L
        );
    }

    @Test
    void returnsValidationEnvelopeForMissingAndUnsupportedQueryParameters() throws Exception {
        AdminAnalyticsController controller = new AdminAnalyticsController(
                mock(AdminAnalyticsQueryService.class),
                queryObservation()
        );
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();

        mockMvc.perform(get("/api/v1/admin/analytics/overview")
                        .param("from", "2026-07-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.to").exists());

        mockMvc.perform(get("/api/v1/admin/analytics/demand/timeseries")
                        .param("from", "2026-07-01T00:00:00Z")
                        .param("to", "2026-07-02T00:00:00Z")
                        .param("bucket", "MONTH"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.bucket").exists());

        mockMvc.perform(get("/api/v1/admin/analytics/demand/heatmap")
                        .param("from", "2026-07-01T00:00:00Z")
                        .param("to", "2026-07-02T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.cellSizeMeters").exists());
    }

    private AnalyticsQueryObservation queryObservation() {
        return new AnalyticsQueryObservation(
                new SimpleMeterRegistry(),
                Clock.fixed(Instant.parse("2026-07-28T03:00:00Z"), ZoneOffset.UTC)
        );
    }
}
