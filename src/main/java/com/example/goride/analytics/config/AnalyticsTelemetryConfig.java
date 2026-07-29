package com.example.goride.analytics.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        AnalyticsTelemetryProperties.class,
        AnalyticsSpatialProperties.class
})
public class AnalyticsTelemetryConfig {
}
