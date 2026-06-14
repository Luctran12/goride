package com.example.goride.driver.config;

import com.example.goride.driver.service.availability.DriverAvailabilityProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(DriverAvailabilityProperties.class)
public class DriverAvailabilityConfig {
    @Bean
    @ConditionalOnMissingBean
    Clock systemClock() {
        return Clock.systemUTC();
    }
}
