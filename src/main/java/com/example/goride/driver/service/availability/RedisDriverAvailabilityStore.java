package com.example.goride.driver.service.availability;

import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class RedisDriverAvailabilityStore implements DriverAvailabilityStore {
    private static final String ONLINE_DRIVERS_KEY = "drivers:online";
    private static final Duration DRIVER_STATUS_TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;

    public RedisDriverAvailabilityStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void markAvailable(DriverAvailability availability) {
        String driverId = String.valueOf(availability.driverId());
        redisTemplate.opsForGeo().add(
                ONLINE_DRIVERS_KEY,
                new Point(availability.longitude().doubleValue(), availability.latitude().doubleValue()),
                driverId
        );
        redisTemplate.opsForValue().set(statusKey(driverId), "AVAILABLE", DRIVER_STATUS_TTL);
        redisTemplate.opsForHash().putAll(metaKey(driverId), Map.of(
                "vehicleType", availability.vehicleType().name(),
                "rating", availability.rating().toPlainString(),
                "name", nullToBlank(availability.driverName()),
                "avatarUrl", nullToBlank(availability.avatarUrl())
        ));
        redisTemplate.expire(metaKey(driverId), DRIVER_STATUS_TTL);
    }

    @Override
    public void markOffline(Long driverId) {
        String driverIdValue = String.valueOf(driverId);
        redisTemplate.opsForGeo().remove(ONLINE_DRIVERS_KEY, driverIdValue);
        redisTemplate.delete(List.of(statusKey(driverIdValue), metaKey(driverIdValue)));
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
