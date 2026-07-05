package com.example.goride.booking.config;

import com.example.goride.booking.service.ScheduledRideProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ScheduledRideProperties.class)
public class BookingConfig {
}