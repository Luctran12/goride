package com.example.goride.matching.domain;

import java.time.Instant;

public record DriverOffer(
        Long tripId,
        DriverCandidate candidate,
        int attempt,
        Instant offerExpiresAt
) {
}
