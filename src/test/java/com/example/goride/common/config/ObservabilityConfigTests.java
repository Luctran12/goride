package com.example.goride.common.config;

import com.example.goride.common.logging.RequestCorrelationFilter;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.actuate.info.Info;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityConfigTests {
    private final ObservabilityConfig config = new ObservabilityConfig();
    private final ObservationFilter requestIdObservationFilter = config.requestIdObservationFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void addsSanitizedRequestIdAsHighCardinalityObservationTag() {
        MDC.put(RequestCorrelationFilter.REQUEST_ID_MDC_KEY, "fe-request-123");
        Observation.Context context = new Observation.Context();

        requestIdObservationFilter.map(context);

        assertThat(context.getHighCardinalityKeyValues())
                .contains(KeyValue.of(ObservabilityConfig.REQUEST_ID_SPAN_KEY, "fe-request-123"));
    }

    @Test
    void leavesObservationUntouchedWhenRequestIdIsMissing() {
        Observation.Context context = new Observation.Context();

        requestIdObservationFilter.map(context);

        assertThat(context.getHighCardinalityKeyValues()).isEmpty();
    }

    @Test
    void contributesTypedTracingFlagsWithoutCollectorDetails() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("management.tracing.enabled", "true")
                .withProperty("management.otlp.tracing.export.enabled", "false");
        Info.Builder builder = new Info.Builder();

        config.observabilityInfoContributor(environment).contribute(builder);

        assertThat(builder.build().getDetails())
                .containsEntry("observability", Map.of(
                        "tracingEnabled", true,
                        "otlpExportEnabled", false
                ));
    }
}
