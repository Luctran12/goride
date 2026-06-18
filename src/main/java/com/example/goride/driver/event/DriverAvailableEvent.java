package com.example.goride.driver.event;

public record DriverAvailableEvent(Long driverId) {
    public DriverAvailableEvent {
        if (driverId == null) {
            throw new IllegalArgumentException("driverId must not be null");
        }
    }
}
