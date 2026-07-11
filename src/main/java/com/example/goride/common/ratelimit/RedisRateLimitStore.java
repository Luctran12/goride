package com.example.goride.common.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Component
@ConditionalOnProperty(
        name = "app.security.rate-limit.store",
        havingValue = "redis"
)
public class RedisRateLimitStore implements RateLimitStore {
    private static final String SCRIPT_SOURCE = """
            local capacity = tonumber(ARGV[1])
            local refillTokens = tonumber(ARGV[2])
            local refillPeriodMillis = tonumber(ARGV[3])
            local ttlMillis = tonumber(ARGV[4])

            local time = redis.call('TIME')
            local nowMillis = (tonumber(time[1]) * 1000) + math.floor(tonumber(time[2]) / 1000)
            local state = redis.call('HMGET', KEYS[1], 'tokens', 'last_refill_millis')
            local tokens = tonumber(state[1])
            local lastRefillMillis = tonumber(state[2])

            if tokens == nil or lastRefillMillis == nil then
                tokens = capacity
                lastRefillMillis = nowMillis
            else
                local elapsedPeriods = math.floor((nowMillis - lastRefillMillis) / refillPeriodMillis)
                if elapsedPeriods > 0 then
                    tokens = math.min(capacity, tokens + (elapsedPeriods * refillTokens))
                    lastRefillMillis = lastRefillMillis + (elapsedPeriods * refillPeriodMillis)
                else
                    tokens = math.min(tokens, capacity)
                end
            end

            local allowed = 0
            if tokens > 0 then
                tokens = tokens - 1
                allowed = 1
            end

            local nextRefillMillis = lastRefillMillis + refillPeriodMillis
            local retryAfterSeconds = 0
            if allowed == 0 then
                retryAfterSeconds = math.max(1, math.ceil((nextRefillMillis - nowMillis) / 1000))
            end

            redis.call(
                'HSET',
                KEYS[1],
                'tokens',
                tokens,
                'last_refill_millis',
                lastRefillMillis
            )
            redis.call('PEXPIRE', KEYS[1], ttlMillis)

            return {
                allowed,
                tokens,
                retryAfterSeconds,
                math.floor(nextRefillMillis / 1000)
            }
            """;
    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> CONSUME_SCRIPT =
            new DefaultRedisScript<>(SCRIPT_SOURCE, List.class);

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimitStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public RateLimitDecision consume(String key, RateLimitProperties properties) {
        long refillPeriodMillis = Math.multiplyExact(properties.refillPeriodSeconds(), 1_000L);
        long ttlMillis = bucketTtlMillis(properties, refillPeriodMillis);
        List<?> result = redisTemplate.execute(
                CONSUME_SCRIPT,
                List.of(redisKey(key, properties)),
                Integer.toString(properties.capacity()),
                Integer.toString(properties.refillTokens()),
                Long.toString(refillPeriodMillis),
                Long.toString(ttlMillis)
        );
        if (result == null || result.size() != 4) {
            throw new IllegalStateException("Redis rate limit script returned an invalid result");
        }

        boolean allowed = number(result.get(0), "allowed").longValue() == 1L;
        int remaining = number(result.get(1), "remaining").intValue();
        long retryAfterSeconds = number(result.get(2), "retryAfterSeconds").longValue();
        long resetEpochSeconds = number(result.get(3), "resetEpochSeconds").longValue();
        return new RateLimitDecision(
                allowed,
                properties.capacity(),
                remaining,
                retryAfterSeconds,
                resetEpochSeconds
        );
    }

    private long bucketTtlMillis(RateLimitProperties properties, long refillPeriodMillis) {
        long periodsToFull = (properties.capacity() + (long) properties.refillTokens() - 1)
                / properties.refillTokens();
        return Math.multiplyExact(refillPeriodMillis, Math.max(2, periodsToFull + 1));
    }

    private String redisKey(String key, RateLimitProperties properties) {
        return properties.redisKeyPrefix() + sha256(key);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private Number number(Object value, String field) {
        if (value instanceof Number number) {
            return number;
        }
        throw new IllegalStateException("Redis rate limit script returned invalid " + field);
    }
}
