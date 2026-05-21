package com.example.goride.tracking.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class RedisLatestDriverLocationStore implements LatestDriverLocationStore {
    private static final Duration DRIVER_LOCATION_TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redisTemplate;

    public RedisLatestDriverLocationStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(LatestDriverLocation location) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("tripId", String.valueOf(location.tripId()));
        fields.put("driverId", String.valueOf(location.driverId()));
        fields.put("lat", location.lat().toPlainString());
        fields.put("lng", location.lng().toPlainString());
        fields.put("updatedAt", location.updatedAt().toString());
        putOptional(fields, "bearing", location.bearing());
        putOptional(fields, "speed", location.speed());

        redisTemplate.opsForHash().putAll(locationKey(location.driverId()), fields);
        redisTemplate.expire(locationKey(location.driverId()), DRIVER_LOCATION_TTL);
    }

    @Override
    public Optional<LatestDriverLocation> findByDriverId(Long driverId) {
        Map<Object, Object> fields = redisTemplate.opsForHash().entries(locationKey(driverId));
        if (fields.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new LatestDriverLocation(
                parseLong(fields.get("tripId")),
                parseLong(fields.get("driverId")),
                parseBigDecimal(fields.get("lat")),
                parseBigDecimal(fields.get("lng")),
                parseOptionalBigDecimal(fields.get("bearing")),
                parseOptionalBigDecimal(fields.get("speed")),
                Instant.parse(String.valueOf(fields.get("updatedAt")))
        ));
    }

    private void putOptional(Map<String, String> fields, String key, BigDecimal value) {
        if (value != null) {
            fields.put(key, value.toPlainString());
        }
    }

    private String locationKey(Long driverId) {
        return "driver:" + driverId + ":location";
    }

    private Long parseLong(Object value) {
        return Long.parseLong(String.valueOf(value));
    }

    private BigDecimal parseBigDecimal(Object value) {
        return new BigDecimal(String.valueOf(value));
    }

    private BigDecimal parseOptionalBigDecimal(Object value) {
        return value == null ? null : parseBigDecimal(value);
    }
}
