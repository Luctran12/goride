package com.example.goride.integration;

import com.example.goride.common.ratelimit.RateLimitDecision;
import com.example.goride.common.ratelimit.RateLimitFilter;
import com.example.goride.common.ratelimit.RateLimitStore;
import com.example.goride.common.ratelimit.RateLimitProperties;
import com.example.goride.common.ratelimit.RedisRateLimitStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "management.endpoint.health.group.readiness.include=readinessState",
        "app.security.rate-limit.store=redis",
        "app.security.rate-limit.enabled=true",
        "app.security.rate-limit.capacity=2",
        "app.security.rate-limit.refill-tokens=2",
        "app.security.rate-limit.refill-period-seconds=60",
        "app.security.rate-limit.use-forwarded-for=true"
})
@AutoConfigureMockMvc
class RedisRateLimitStoreIntegrationTests extends PostgresRedisIntegrationTest {
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RateLimitStore rateLimitStore;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void sharesOneAtomicBucketAcrossStoreInstances() {
        assertThat(rateLimitStore).isInstanceOf(RedisRateLimitStore.class);
        RedisRateLimitStore firstInstance = (RedisRateLimitStore) rateLimitStore;
        RedisRateLimitStore secondInstance = new RedisRateLimitStore(redisTemplate);
        RateLimitProperties properties = properties(2, 2, 60);
        String key = "ip:" + UUID.randomUUID();

        RateLimitDecision first = firstInstance.consume(key, properties);
        RateLimitDecision second = secondInstance.consume(key, properties);
        RateLimitDecision rejected = firstInstance.consume(key, properties);

        assertThat(first.allowed()).isTrue();
        assertThat(first.remaining()).isEqualTo(1);
        assertThat(second.allowed()).isTrue();
        assertThat(second.remaining()).isZero();
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.remaining()).isZero();
        assertThat(rejected.retryAfterSeconds()).isBetween(1L, 60L);
        assertThat(rejected.resetEpochSeconds()).isPositive();
    }

    @Test
    void refillsBucketAfterConfiguredPeriod() throws InterruptedException {
        RedisRateLimitStore store = (RedisRateLimitStore) rateLimitStore;
        RateLimitProperties properties = properties(1, 1, 1);
        String key = "ip:" + UUID.randomUUID();

        assertThat(store.consume(key, properties).allowed()).isTrue();
        assertThat(store.consume(key, properties).allowed()).isFalse();

        Thread.sleep(1_100);

        RateLimitDecision refilled = store.consume(key, properties);
        assertThat(refilled.allowed()).isTrue();
        assertThat(refilled.remaining()).isZero();
    }

    @Test
    void preservesHttpRateLimitContractWithRedisStore() throws Exception {
        String clientIp = "198.51.100." + Math.floorMod(UUID.randomUUID().hashCode(), 200);
        String body = """
                {
                  "phone": "0900000000",
                  "password": "wrong-password"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", clientIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(RateLimitFilter.RATE_LIMIT_REMAINING_HEADER, "1"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", clientIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(RateLimitFilter.RATE_LIMIT_REMAINING_HEADER, "0"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", clientIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(RateLimitFilter.RATE_LIMIT_LIMIT_HEADER, "2"))
                .andExpect(header().string(RateLimitFilter.RATE_LIMIT_REMAINING_HEADER, "0"))
                .andExpect(jsonPath("$.error.code").value("RATE_LIMIT_EXCEEDED"));
    }

    private RateLimitProperties properties(int capacity, int refillTokens, long refillPeriodSeconds) {
        return new RateLimitProperties(
                true,
                RateLimitProperties.Store.REDIS,
                capacity,
                refillTokens,
                refillPeriodSeconds,
                10_000,
                "test:rate-limit:",
                List.of("/actuator/**"),
                false
        );
    }
}
