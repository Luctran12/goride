package com.example.goride.matching.telemetry;

import java.time.Instant;
import java.util.Set;

public record ExpiredMatchingOffer(
        Long tripId,
        Long driverId,
        int attempt,
        Instant expiresAt,
        Set<Long> excludedDriverIds
) {
    public ExpiredMatchingOffer {
        excludedDriverIds = excludedDriverIds == null ? Set.of() : Set.copyOf(excludedDriverIds);
    }
}
