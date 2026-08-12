package com.example.goride.analytics.dto;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DemandForecastPrivacyContractTests {
    private static final Set<String> FORBIDDEN = Set.of(
            "tripid", "userid", "driverid", "passengerid",
            "email", "phone", "artifacturi"
    );

    @Test
    void servingRecordsExcludeTripUserContactAndArtifactLocationFields() {
        Stream.of(
                ForecastDemandResponse.class,
                ForecastDemandResponse.Feature.class,
                ForecastDemandResponse.Properties.class,
                ForecastHotspotsResponse.class,
                ForecastHotspotsResponse.Hotspot.class,
                ForecastResponseMetadata.class,
                ForecastRunResponse.class,
                ModelVersionResponse.class,
                ProcessingStatusResponse.class,
                DataQualityResponse.class
        ).forEach(type -> assertThat(componentNames(type))
                .describedAs("privacy-safe fields for %s", type.getSimpleName())
                .doesNotContainAnyElementsOf(FORBIDDEN));
    }

    private Set<String> componentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
    }
}
