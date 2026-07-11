package com.example.goride.common.ratelimit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitPropertiesTests {
    @Test
    void defaultsToInMemoryStoreAndDedicatedRedisPrefix() {
        RateLimitProperties properties = new RateLimitProperties(
                null,
                null,
                0,
                0,
                0,
                0,
                null,
                null,
                null
        );

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.store()).isEqualTo(RateLimitProperties.Store.MEMORY);
        assertThat(properties.redisKeyPrefix()).isEqualTo("goride:rate-limit:");
        assertThat(properties.capacity()).isEqualTo(120);
        assertThat(properties.refillTokens()).isEqualTo(120);
        assertThat(properties.refillPeriodSeconds()).isEqualTo(60);
    }

    @Test
    void preservesRedisStoreAndNormalizesPrefix() {
        RateLimitProperties properties = new RateLimitProperties(
                true,
                RateLimitProperties.Store.REDIS,
                10,
                5,
                30,
                500,
                "  custom:rate-limit:  ",
                List.of("/actuator/**"),
                true
        );

        assertThat(properties.store()).isEqualTo(RateLimitProperties.Store.REDIS);
        assertThat(properties.redisKeyPrefix()).isEqualTo("custom:rate-limit:");
        assertThat(properties.shouldUseForwardedFor()).isTrue();
    }
}
