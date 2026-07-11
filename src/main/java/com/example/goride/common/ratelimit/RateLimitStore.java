package com.example.goride.common.ratelimit;

public interface RateLimitStore {
    RateLimitDecision consume(String key, RateLimitProperties properties);
}
