package com.example.goride.matching.service;

import com.example.goride.driver.domain.VehicleType;
import com.example.goride.matching.domain.MatchingRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisDriverCandidateStoreTests {
    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private GeoOperations<String, String> geoOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private RedisDriverCandidateStore candidateStore;

    @BeforeEach
    void setUp() {
        candidateStore = new RedisDriverCandidateStore(redisTemplate);
    }

    @Test
    void findAvailableCandidatesFiltersByStatusAndVehicleType() {
        when(redisTemplate.opsForGeo()).thenReturn(geoOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(geoOperations.radius(eq("drivers:online"), any(Circle.class), any(RedisGeoCommands.GeoRadiusCommandArgs.class)))
                .thenReturn(new GeoResults<>(List.of(
                        geoResult("10", 0.25),
                        geoResult("11", 0.35),
                        geoResult("12", 0.45)
                ), Metrics.KILOMETERS));
        when(valueOperations.get("driver:10:status")).thenReturn("AVAILABLE");
        when(valueOperations.get("driver:11:status")).thenReturn("BUSY");
        when(valueOperations.get("driver:12:status")).thenReturn("AVAILABLE");
        when(hashOperations.entries("driver:10:meta")).thenReturn(Map.of(
                "vehicleType", "CAR_4_SEAT",
                "rating", "4.8",
                "name", "Driver 10",
                "avatarUrl", ""
        ));
        when(hashOperations.entries("driver:12:meta")).thenReturn(Map.of(
                "vehicleType", "MOTORBIKE",
                "rating", "5.0"
        ));

        var candidates = candidateStore.findAvailableCandidates(request(VehicleType.CAR_4_SEAT));

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).driverId()).isEqualTo(10L);
        assertThat(candidates.get(0).distanceMeters()).isEqualTo(250L);
        assertThat(candidates.get(0).vehicleType()).isEqualTo(VehicleType.CAR_4_SEAT);
        assertThat(candidates.get(0).rating()).isEqualByComparingTo(BigDecimal.valueOf(4.8));
    }

    @Test
    void tryLockCandidateUsesRedisSetNxWithTtl() {
        Duration ttl = Duration.ofSeconds(30);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("driver:10:lock", "99", ttl)).thenReturn(true);

        boolean locked = candidateStore.tryLockCandidate(99L, 10L, ttl);

        assertThat(locked).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void recordTripMatchingWritesOfferStateWithTtl() {
        Duration ttl = Duration.ofMinutes(5);
        Instant expiresAt = Instant.parse("2026-05-19T08:00:00Z");
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        candidateStore.recordTripMatching(99L, 10L, 1, expiresAt, ttl);

        ArgumentCaptor<Map<Object, Object>> fieldsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(hashOperations).putAll(eq("trip:99:matching"), fieldsCaptor.capture());
        verify(redisTemplate).expire("trip:99:matching", ttl);
        assertThat(fieldsCaptor.getValue())
                .containsEntry("attempt", "1")
                .containsEntry("offeredDriverId", "10")
                .containsEntry("offerExpiresAt", "2026-05-19T08:00:00Z");
    }

    private MatchingRequest request(VehicleType vehicleType) {
        return new MatchingRequest(
                99L,
                vehicleType,
                BigDecimal.valueOf(10.7769),
                BigDecimal.valueOf(106.7009),
                BigDecimal.valueOf(5),
                3
        );
    }

    private GeoResult<RedisGeoCommands.GeoLocation<String>> geoResult(String driverId, double distanceKm) {
        return new GeoResult<>(
                new RedisGeoCommands.GeoLocation<>(driverId, new Point(106.7009, 10.7769)),
                new Distance(distanceKm, Metrics.KILOMETERS)
        );
    }
}
