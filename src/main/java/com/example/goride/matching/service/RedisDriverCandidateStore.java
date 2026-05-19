package com.example.goride.matching.service;

import com.example.goride.driver.domain.VehicleType;
import com.example.goride.matching.domain.DriverCandidate;
import com.example.goride.matching.domain.MatchingRequest;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class RedisDriverCandidateStore implements DriverCandidateStore {
    private static final String ONLINE_DRIVERS_KEY = "drivers:online";
    private static final String AVAILABLE_STATUS = "AVAILABLE";

    private final StringRedisTemplate redisTemplate;

    public RedisDriverCandidateStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public List<DriverCandidate> findAvailableCandidates(MatchingRequest request) {
        Circle searchArea = new Circle(
                new Point(request.pickupLongitude().doubleValue(), request.pickupLatitude().doubleValue()),
                new Distance(request.radiusKm().doubleValue(), Metrics.KILOMETERS)
        );
        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs
                .newGeoRadiusArgs()
                .includeDistance()
                .sortAscending()
                .limit(Math.max(request.limit() * 3, request.limit()));

        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redisTemplate.opsForGeo()
                .radius(ONLINE_DRIVERS_KEY, searchArea, args);
        if (results == null) {
            return List.of();
        }

        return results.getContent().stream()
                .map(result -> toCandidate(result, request.vehicleType()))
                .flatMap(Optional::stream)
                .limit(request.limit())
                .toList();
    }

    @Override
    public boolean tryLockCandidate(Long tripId, Long driverId, Duration lockTtl) {
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(lockKey(driverId), String.valueOf(tripId), lockTtl);
        return Boolean.TRUE.equals(locked);
    }

    @Override
    public void recordTripMatching(Long tripId, Long driverId, int attempt, Instant offerExpiresAt, Duration ttl) {
        String key = tripMatchingKey(tripId);
        redisTemplate.opsForHash().putAll(key, Map.of(
                "attempt", String.valueOf(attempt),
                "offeredDriverId", String.valueOf(driverId),
                "offerExpiresAt", offerExpiresAt.toString()
        ));
        redisTemplate.expire(key, ttl);
    }

    private Optional<DriverCandidate> toCandidate(
            GeoResult<RedisGeoCommands.GeoLocation<String>> result,
            VehicleType requestedVehicleType
    ) {
        String driverIdValue = result.getContent().getName();
        if (!AVAILABLE_STATUS.equals(redisTemplate.opsForValue().get(statusKey(driverIdValue)))) {
            return Optional.empty();
        }

        Map<Object, Object> meta = redisTemplate.opsForHash().entries(metaKey(driverIdValue));
        VehicleType vehicleType = parseVehicleType(meta.get("vehicleType")).orElse(null);
        if (vehicleType != requestedVehicleType) {
            return Optional.empty();
        }

        try {
            return Optional.of(new DriverCandidate(
                    Long.parseLong(driverIdValue),
                    distanceMeters(result),
                    vehicleType,
                    parseRating(meta.get("rating")),
                    stringValue(meta.get("name")),
                    stringValue(meta.get("avatarUrl"))
            ));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private Optional<VehicleType> parseVehicleType(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(VehicleType.valueOf(String.valueOf(value)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private BigDecimal parseRating(Object value) {
        if (value == null) {
            return BigDecimal.valueOf(5.0);
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return BigDecimal.valueOf(5.0);
        }
    }

    private long distanceMeters(GeoResult<RedisGeoCommands.GeoLocation<String>> result) {
        return BigDecimal.valueOf(result.getDistance().in(Metrics.KILOMETERS).getValue())
                .multiply(BigDecimal.valueOf(1000))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String statusKey(String driverId) {
        return "driver:" + driverId + ":status";
    }

    private String metaKey(String driverId) {
        return "driver:" + driverId + ":meta";
    }

    private String lockKey(Long driverId) {
        return "driver:" + driverId + ":lock";
    }

    private String tripMatchingKey(Long tripId) {
        return "trip:" + tripId + ":matching";
    }
}
