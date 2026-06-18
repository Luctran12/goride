package com.example.goride.matching.service;

import com.example.goride.driver.event.DriverAvailableEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class DriverAvailableMatchingListener {
    private final SearchingTripMatchingService searchingTripMatchingService;

    public DriverAvailableMatchingListener(SearchingTripMatchingService searchingTripMatchingService) {
        this.searchingTripMatchingService = searchingTripMatchingService;
    }

    @EventListener
    public void onDriverAvailable(DriverAvailableEvent event) {
        searchingTripMatchingService.matchOpenSearchingTrips(event.driverId());
    }
}
