package com.example.goride.booking.config;

import com.example.goride.booking.service.distance.RoutingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RoutingProperties.class)
public class RoutingConfig {
}
