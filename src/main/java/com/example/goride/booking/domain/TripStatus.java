package com.example.goride.booking.domain;

import java.util.EnumSet;
import java.util.Set;

public enum TripStatus {
    SCHEDULED,
    SEARCHING,
    ACCEPTED,
    ARRIVED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    NO_DRIVER;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == NO_DRIVER;
    }

    public boolean canBeCancelled() {
        return this == SCHEDULED || this == SEARCHING || this == ACCEPTED || this == ARRIVED;
    }

    public static Set<TripStatus> activeStatuses() {
        return EnumSet.of(SCHEDULED, SEARCHING, ACCEPTED, ARRIVED, IN_PROGRESS);
    }
}
