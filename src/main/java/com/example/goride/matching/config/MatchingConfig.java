package com.example.goride.matching.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableConfigurationProperties(MatchingRouteEtaProperties.class)
public class MatchingConfig {
    @Bean(name = "routeEtaExecutor")
    Executor routeEtaExecutor(MatchingRouteEtaProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getExecutorThreads());
        executor.setMaxPoolSize(properties.getExecutorThreads());
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("route-eta-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}