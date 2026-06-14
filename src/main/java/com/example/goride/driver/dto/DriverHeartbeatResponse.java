package com.example.goride.driver.dto;

import java.time.Instant;

public record DriverHeartbeatResponse(
        boolean online,
        Instant heartbeatAt,
        Instant expiresAt
) {
}
