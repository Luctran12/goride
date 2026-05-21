package com.example.goride.matching.domain;

import java.time.Instant;
import java.util.Set;

public record TripMatchingState(
        Long tripId,
        Long offeredDriverId,
        int attempt,
        Instant offerExpiresAt,
        Set<Long> rejectedDriverIds
) {
    public TripMatchingState {
        if (tripId == null) {
            throw new IllegalArgumentException("tripId must not be null");
        }
        if (offeredDriverId == null) {
            throw new IllegalArgumentException("offeredDriverId must not be null");
        }
        if (attempt <= 0) {
            throw new IllegalArgumentException("attempt must be positive");
        }
        if (offerExpiresAt == null) {
            throw new IllegalArgumentException("offerExpiresAt must not be null");
        }
        rejectedDriverIds = rejectedDriverIds == null ? Set.of() : Set.copyOf(rejectedDriverIds);
    }
}
