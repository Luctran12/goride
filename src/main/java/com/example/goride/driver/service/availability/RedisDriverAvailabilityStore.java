package com.example.goride.driver.service.availability;

import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class RedisDriverAvailabilityStore implements DriverAvailabilityStore {
    private static final String ONLINE_DRIVERS_KEY = "drivers:online";

    private final StringRedisTemplate redisTemplate;
    private final DriverAvailabilityProperties properties;

    public RedisDriverAvailabilityStore(
            StringRedisTemplate redisTemplate,
            DriverAvailabilityProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public void markAvailable(DriverAvailability availability) {
        String driverId = String.valueOf(availability.driverId());
        writeLocationAndMetadata(availability, driverId);
        redisTemplate.opsForValue().set(statusKey(driverId), "AVAILABLE", properties.heartbeatTimeout());
    }

    @Override
    public boolean refreshHeartbeat(DriverAvailability availability) {
        String driverId = String.valueOf(availability.driverId());
        String statusKey = statusKey(driverId);
        if (redisTemplate.opsForValue().get(statusKey) == null) {
            return false;
        }

        writeLocationAndMetadata(availability, driverId);
        return Boolean.TRUE.equals(redisTemplate.expire(statusKey, properties.heartbeatTimeout()));
    }

    private void writeLocationAndMetadata(DriverAvailability availability, String driverId) {
        redisTemplate.opsForGeo().add(
                ONLINE_DRIVERS_KEY,
                new Point(availability.longitude().doubleValue(), availability.latitude().doubleValue()),
                driverId
        );
        redisTemplate.opsForHash().putAll(metaKey(driverId), Map.of(
                "vehicleType", availability.vehicleType().name(),
                "rating", availability.rating().toPlainString(),
                "name", nullToBlank(availability.driverName()),
                "avatarUrl", nullToBlank(availability.avatarUrl()),
                "locationUpdatedAt", Instant.now().toString()
        ));
        redisTemplate.expire(metaKey(driverId), properties.heartbeatTimeout());
    }

    @Override
    public Optional<DriverLocation> findLocation(Long driverId) {
        if (driverId == null) {
            return Optional.empty();
        }

        String driverIdValue = String.valueOf(driverId);
        if (redisTemplate.opsForValue().get(statusKey(driverIdValue)) == null) {
            return Optional.empty();
        }

        List<Point> positions = redisTemplate.opsForGeo().position(ONLINE_DRIVERS_KEY, driverIdValue);
        if (positions == null || positions.isEmpty() || positions.get(0) == null) {
            return Optional.empty();
        }

        Object updatedAt = redisTemplate.opsForHash().get(metaKey(driverIdValue), "locationUpdatedAt");
        if (updatedAt == null) {
            return Optional.empty();
        }

        try {
            Point position = positions.get(0);
            return Optional.of(new DriverLocation(
                    driverId,
                    BigDecimal.valueOf(position.getY()),
                    BigDecimal.valueOf(position.getX()),
                    Instant.parse(String.valueOf(updatedAt))
            ));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    @Override
    public void markOffline(Long driverId) {
        String driverIdValue = String.valueOf(driverId);
        redisTemplate.opsForGeo().remove(ONLINE_DRIVERS_KEY, driverIdValue);
        redisTemplate.delete(List.of(statusKey(driverIdValue), metaKey(driverIdValue)));
    }

    @Override
    public void updateRating(Long driverId, BigDecimal rating) {
        if (driverId == null || rating == null) {
            return;
        }

        String driverIdValue = String.valueOf(driverId);
        String statusKey = statusKey(driverIdValue);
        String metaKey = metaKey(driverIdValue);
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(statusKey))
                || !Boolean.TRUE.equals(redisTemplate.hasKey(metaKey))) {
            return;
        }

        redisTemplate.opsForHash().put(metaKey, "rating", rating.toPlainString());
        redisTemplate.expire(metaKey, properties.heartbeatTimeout());
    }

    private String statusKey(String driverId) {
        return "driver:" + driverId + ":status";
    }

    private String metaKey(String driverId) {
        return "driver:" + driverId + ":meta";
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}
