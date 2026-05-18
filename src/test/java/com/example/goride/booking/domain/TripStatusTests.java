package com.example.goride.booking.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TripStatusTests {
    @Test
    void activeStatusesExcludeTerminalStates() {
        assertThat(TripStatus.activeStatuses())
                .containsExactlyInAnyOrder(
                        TripStatus.SEARCHING,
                        TripStatus.ACCEPTED,
                        TripStatus.ARRIVED,
                        TripStatus.IN_PROGRESS
                );
    }

    @Test
    void onlyEarlyTripStatesCanBeCancelled() {
        assertThat(TripStatus.SEARCHING.canBeCancelled()).isTrue();
        assertThat(TripStatus.ACCEPTED.canBeCancelled()).isTrue();
        assertThat(TripStatus.ARRIVED.canBeCancelled()).isTrue();
        assertThat(TripStatus.IN_PROGRESS.canBeCancelled()).isFalse();
        assertThat(TripStatus.COMPLETED.canBeCancelled()).isFalse();
    }
}
