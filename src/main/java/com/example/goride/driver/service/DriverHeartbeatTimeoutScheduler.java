package com.example.goride.driver.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "app.driver.availability.scheduler",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class DriverHeartbeatTimeoutScheduler {
    private final DriverHeartbeatTimeoutService timeoutService;

    public DriverHeartbeatTimeoutScheduler(DriverHeartbeatTimeoutService timeoutService) {
        this.timeoutService = timeoutService;
    }

    @Scheduled(
            fixedDelayString = "${app.driver.availability.scheduler.fixed-delay-ms:15000}",
            initialDelayString = "${app.driver.availability.scheduler.initial-delay-ms:30000}"
    )
    public void expireStaleDrivers() {
        timeoutService.expireStaleDrivers();
    }
}
