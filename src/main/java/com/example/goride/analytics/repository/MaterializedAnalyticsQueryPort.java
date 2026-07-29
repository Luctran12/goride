package com.example.goride.analytics.repository;

import java.time.Instant;
import java.util.Optional;

public interface MaterializedAnalyticsQueryPort extends DirectAnalyticsQueryPort {
    Optional<Snapshot> currentSnapshot();

    record Snapshot(
            Instant completedAt,
            String reportingTimezone,
            int projectedSrid,
            int baseCellSizeMeters
    ) {
    }
}
