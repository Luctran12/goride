package com.example.goride.tracking.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public interface LatestDriverLocationStore {
    void save(LatestDriverLocation location);

    Optional<LatestDriverLocation> findByDriverId(Long driverId);

    record LatestDriverLocation(
            Long tripId,
            Long driverId,
            BigDecimal lat,
            BigDecimal lng,
            BigDecimal bearing,
            BigDecimal speed,
            Instant updatedAt
    ) {
    }
}
