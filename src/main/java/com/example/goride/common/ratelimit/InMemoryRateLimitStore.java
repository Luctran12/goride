package com.example.goride.common.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(
        name = "app.security.rate-limit.store",
        havingValue = "memory",
        matchIfMissing = true
)
public class InMemoryRateLimitStore implements RateLimitStore {
    private final Clock clock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public InMemoryRateLimitStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public RateLimitDecision consume(String key, RateLimitProperties properties) {
        long nowMillis = clock.millis();
        long refillPeriodMillis = properties.refillPeriodSeconds() * 1_000;
        evictIdleBuckets(properties, nowMillis, refillPeriodMillis);

        Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(properties.capacity(), nowMillis));
        synchronized (bucket) {
            bucket.refill(properties, nowMillis, refillPeriodMillis);
            bucket.lastSeenMillis = nowMillis;
            long nextRefillMillis = bucket.lastRefillMillis + refillPeriodMillis;
            long resetEpochSeconds = nextRefillMillis / 1_000;
            if (bucket.tokens > 0) {
                bucket.tokens--;
                return new RateLimitDecision(true, properties.capacity(), bucket.tokens, 0, resetEpochSeconds);
            }
            long retryAfterSeconds = Math.max(1, ceilSeconds(nextRefillMillis - nowMillis));
            return new RateLimitDecision(false, properties.capacity(), 0, retryAfterSeconds, resetEpochSeconds);
        }
    }

    int bucketCount() {
        return buckets.size();
    }

    private void evictIdleBuckets(RateLimitProperties properties, long nowMillis, long refillPeriodMillis) {
        if (buckets.size() <= properties.maxKeys()) {
            return;
        }
        long idleBeforeMillis = nowMillis - (refillPeriodMillis * 2);
        Iterator<Map.Entry<String, Bucket>> iterator = buckets.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Bucket> entry = iterator.next();
            if (entry.getValue().lastSeenMillis < idleBeforeMillis) {
                iterator.remove();
            }
        }
    }

    private long ceilSeconds(long millis) {
        return (millis + 999) / 1_000;
    }

    private static final class Bucket {
        private int tokens;
        private long lastRefillMillis;
        private long lastSeenMillis;

        private Bucket(int tokens, long nowMillis) {
            this.tokens = tokens;
            this.lastRefillMillis = nowMillis;
            this.lastSeenMillis = nowMillis;
        }

        private void refill(RateLimitProperties properties, long nowMillis, long refillPeriodMillis) {
            long elapsedPeriods = (nowMillis - lastRefillMillis) / refillPeriodMillis;
            if (elapsedPeriods <= 0) {
                tokens = Math.min(tokens, properties.capacity());
                return;
            }
            long refillAmount = elapsedPeriods * properties.refillTokens();
            tokens = (int) Math.min(properties.capacity(), tokens + refillAmount);
            lastRefillMillis += elapsedPeriods * refillPeriodMillis;
        }
    }
}