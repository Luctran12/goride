package com.example.goride.driver.service;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DriverHeartbeatTimeoutSchedulerTests {
    @Test
    void delegatesStaleDriverCleanup() {
        DriverHeartbeatTimeoutService timeoutService = mock(DriverHeartbeatTimeoutService.class);
        DriverHeartbeatTimeoutScheduler scheduler = new DriverHeartbeatTimeoutScheduler(timeoutService);

        scheduler.expireStaleDrivers();

        verify(timeoutService).expireStaleDrivers();
    }
}
