package com.example.goride.location.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ThreeWordLocationProperties.class)
public class LocationConfig {
}
