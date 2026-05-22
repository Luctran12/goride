package com.example.goride.matching.service;

import com.example.goride.driver.domain.VehicleType;
import com.example.goride.matching.domain.DriverCandidate;
import com.example.goride.matching.domain.MatchingRequest;
import com.example.goride.matching.domain.TripMatchingState;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RedisDriverCandidateStore implements DriverCandidateStore {
    private static final String ONLINE_DRIVERS_KEY = "drivers:online";
    private static final String ACTIVE_MATCHING_TRIPS_KEY = "matching:activeTrips";
    private static final String AVAILABLE_STATUS = "AVAILABLE";
    private static final String BUSY_STATUS = "BUSY";
    private static final Duration AVAILABLE_STATUS_TTL = Duration.ofSeconds(60);
    private static final Duration BUSY_STATUS_TTL = Duration.ofHours(12);

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
        recordTripMatching(tripId, driverId, attempt, offerExpiresAt, Set.of(), ttl);
    }

    @Override
    public void recordTripMatching(
            Long tripId,
            Long driverId,
            int attempt,
            Instant offerExpiresAt,
            Collection<Long> rejectedDriverIds,
            Duration ttl
    ) {
        String key = tripMatchingKey(tripId);
        redisTemplate.opsForHash().putAll(key, Map.of(
                "attempt", String.valueOf(attempt),
                "offeredDriverId", String.valueOf(driverId),
                "offerExpiresAt", offerExpiresAt.toString(),
                "rejectedDriverIds", serializeDriverIds(rejectedDriverIds)
        ));
        redisTemplate.expire(key, ttl);
        redisTemplate.opsForSet().add(ACTIVE_MATCHING_TRIPS_KEY, String.valueOf(tripId));
    }

    @Override
    public Optional<TripMatchingState> findTripMatching(Long tripId) {
        Map<Object, Object> fields = redisTemplate.opsForHash().entries(tripMatchingKey(tripId));
        if (fields == null || fields.isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(new TripMatchingState(
                    tripId,
                    Long.parseLong(requiredField(fields, "offeredDriverId")),
                    Integer.parseInt(requiredField(fields, "attempt")),
                    Instant.parse(requiredField(fields, "offerExpiresAt")),
                    parseDriverIds(stringValue(fields.get("rejectedDriverIds")))
            ));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Set<Long> findActiveMatchingTripIds() {
        Set<String> tripIds = redisTemplate.opsForSet().members(ACTIVE_MATCHING_TRIPS_KEY);
        if (tripIds == null || tripIds.isEmpty()) {
            return Set.of();
        }
        return tripIds.stream()
                .map(this::parseLong)
                .flatMap(Optional::stream)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void clearTripMatching(Long tripId) {
        redisTemplate.delete(tripMatchingKey(tripId));
        redisTemplate.opsForSet().remove(ACTIVE_MATCHING_TRIPS_KEY, String.valueOf(tripId));
    }

    @Override
    public void releaseCandidateLock(Long driverId) {
        redisTemplate.delete(lockKey(driverId));
    }

    @Override
    public void markCandidateBusy(Long driverId) {
        redisTemplate.opsForValue().set(statusKey(String.valueOf(driverId)), BUSY_STATUS, BUSY_STATUS_TTL);
    }

    @Override
    public void markCandidateAvailable(Long driverId) {
        redisTemplate.opsForValue().set(statusKey(String.valueOf(driverId)), AVAILABLE_STATUS, AVAILABLE_STATUS_TTL);
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

    private String requiredField(Map<Object, Object> fields, String fieldName) {
        Object value = fields.get(fieldName);
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is missing");
        }
        return String.valueOf(value);
    }

    private String serializeDriverIds(Collection<Long> driverIds) {
        if (driverIds == null || driverIds.isEmpty()) {
            return "";
        }
        return driverIds.stream()
                .sorted(Comparator.naturalOrder())
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private Set<Long> parseDriverIds(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .filter(driverId -> !driverId.isBlank())
                .map(Long::parseLong)
                .collect(Collectors.toUnmodifiableSet());
    }

    private Optional<Long> parseLong(String value) {
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }
}
