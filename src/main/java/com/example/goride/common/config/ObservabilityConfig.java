package com.example.goride.common.config;

import com.example.goride.common.logging.RequestCorrelationFilter;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.ObservationFilter;
import org.slf4j.MDC;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.Map;

@Configuration
public class ObservabilityConfig {
    public static final String REQUEST_ID_SPAN_KEY = "request.id";

    @Bean
    ObservationFilter requestIdObservationFilter() {
        return context -> {
            String requestId = MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY);
            if (StringUtils.hasText(requestId)) {
                context.addHighCardinalityKeyValue(KeyValue.of(REQUEST_ID_SPAN_KEY, requestId));
            }
            return context;
        };
    }

    @Bean
    InfoContributor observabilityInfoContributor(Environment environment) {
        return builder -> builder.withDetail("observability", Map.of(
                "tracingEnabled",
                environment.getProperty("management.tracing.enabled", Boolean.class, false),
                "otlpExportEnabled",
                environment.getProperty("management.otlp.tracing.export.enabled", Boolean.class, false)
        ));
    }
}
