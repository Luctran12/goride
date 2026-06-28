package com.example.goride.common.ratelimit;

public record RateLimitDecision(
        boolean allowed,
        int limit,
        int remaining,
        long retryAfterSeconds,
        long resetEpochSeconds
) {
}