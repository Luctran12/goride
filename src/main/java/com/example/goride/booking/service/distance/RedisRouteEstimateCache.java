package com.example.goride.booking.service.distance;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Component
public class RedisRouteEstimateCache implements RouteEstimateCache {
    private static final Logger log = LoggerFactory.getLogger(RedisRouteEstimateCache.class);
    private static final String KEY_PREFIX = "routing:estimate:v1:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RoutingEstimateCacheProperties properties;

    public RedisRouteEstimateCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            RoutingEstimateCacheProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public Optional<DistanceEstimate> find(
            String routingProfile,
            Location pickup,
            Location dropoff
    ) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }

        String key = cacheKey(routingProfile, pickup, dropoff);
        final String payload;
        try {
            payload = redisTemplate.opsForValue().get(key);
        } catch (RuntimeException exception) {
            log.warn("Unable to read route estimate cache key={} error={}", key, exception.getMessage());
            return Optional.empty();
        }
        if (payload == null || payload.isBlank()) {
            return Optional.empty();
        }

        try {
            CacheEntry entry = objectMapper.readValue(payload, CacheEntry.class);
            return Optional.of(entry.toEstimate());
        } catch (Exception exception) {
            log.warn("Discarding invalid route estimate cache entry key={} error={}", key, exception.getMessage());
            evictQuietly(key);
            return Optional.empty();
        }
    }

    @Override
    public void put(
            String routingProfile,
            Location pickup,
            Location dropoff,
            DistanceEstimate estimate
    ) {
        if (!properties.isEnabled()) {
            return;
        }

        String key = cacheKey(routingProfile, pickup, dropoff);
        try {
            String payload = objectMapper.writeValueAsString(CacheEntry.from(estimate));
            redisTemplate.opsForValue().set(key, payload, properties.ttl());
        } catch (Exception exception) {
            log.warn("Unable to write route estimate cache key={} error={}", key, exception.getMessage());
        }
    }

    String cacheKey(String routingProfile, Location pickup, Location dropoff) {
        if (routingProfile == null || routingProfile.isBlank()) {
            throw new IllegalArgumentException("routingProfile must not be blank");
        }
        return KEY_PREFIX
                + routingProfile.strip()
                + ":"
                + coordinate(pickup.latitude())
                + ":"
                + coordinate(pickup.longitude())
                + ":"
                + coordinate(dropoff.latitude())
                + ":"
                + coordinate(dropoff.longitude());
    }

    private String coordinate(BigDecimal value) {
        BigDecimal rounded = value.setScale(properties.getCoordinateScale(), RoundingMode.HALF_UP);
        if (rounded.signum() == 0) {
            rounded = BigDecimal.ZERO.setScale(properties.getCoordinateScale());
        }
        return rounded.toPlainString();
    }

    private void evictQuietly(String key) {
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException exception) {
            log.debug("Unable to evict invalid route estimate cache entry key={}", key, exception);
        }
    }

    private record CacheEntry(
            BigDecimal distanceKm,
            int durationMinutes
    ) {
        private static CacheEntry from(DistanceEstimate estimate) {
            return new CacheEntry(estimate.distanceKm(), estimate.durationMinutes());
        }

        private DistanceEstimate toEstimate() {
            return new DistanceEstimate(distanceKm, durationMinutes);
        }
    }
}
