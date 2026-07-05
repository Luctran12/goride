package com.example.goride.booking.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "app.booking.scheduled-rides.scheduler",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ScheduledRideDispatchScheduler {
    private final ScheduledRideDispatchService dispatchService;

    public ScheduledRideDispatchScheduler(ScheduledRideDispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @Scheduled(
            fixedDelayString = "${app.booking.scheduled-rides.scheduler.fixed-delay-ms:30000}",
            initialDelayString = "${app.booking.scheduled-rides.scheduler.initial-delay-ms:30000}"
    )
    public void dispatchDueScheduledTrips() {
        dispatchService.dispatchDueScheduledTrips();
    }
}