package com.example.goride.chat.service;

import com.example.goride.chat.config.TripMessageRateLimitProperties;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.common.ratelimit.RateLimitDecision;
import com.example.goride.common.ratelimit.RateLimitProperties;
import com.example.goride.common.ratelimit.RateLimitStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class TripMessageRateLimiter {
    private static final int MAX_KEYS = 100_000;

    private final RateLimitStore rateLimitStore;
    private final TripMessageRateLimitProperties properties;
    private final RateLimitProperties storeProperties;

    public TripMessageRateLimiter(
            RateLimitStore rateLimitStore,
            RateLimitProperties globalProperties,
            TripMessageRateLimitProperties properties
    ) {
        this.rateLimitStore = rateLimitStore;
        this.properties = properties;
        this.storeProperties = new RateLimitProperties(
                true,
                globalProperties.store(),
                properties.capacity(),
                properties.refillTokens(),
                properties.refillPeriodSeconds(),
                MAX_KEYS,
                properties.redisKeyPrefix(),
                List.of(),
                false
        );
    }

    public void checkAllowed(Long userId) {
        if (!properties.isEnabled()) {
            return;
        }
        RateLimitDecision decision;
        try {
            decision = rateLimitStore.consume("trip-message:user:" + userId, storeProperties);
        } catch (RuntimeException exception) {
            throw new BusinessException(
                    ErrorCode.RATE_LIMIT_STORE_UNAVAILABLE,
                    ErrorCode.RATE_LIMIT_STORE_UNAVAILABLE.defaultMessage(),
                    Map.of("retryAfterSeconds", 1)
            );
        }
        if (!decision.allowed()) {
            throw new BusinessException(
                    ErrorCode.RATE_LIMIT_EXCEEDED,
                    ErrorCode.RATE_LIMIT_EXCEEDED.defaultMessage(),
                    Map.of("retryAfterSeconds", decision.retryAfterSeconds())
            );
        }
    }
}
