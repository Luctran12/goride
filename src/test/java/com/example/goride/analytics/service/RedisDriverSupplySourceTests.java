package com.example.goride.analytics.service;

import com.example.goride.analytics.service.DriverSupplySource.DriverSupplyStatus;
import com.example.goride.driver.domain.VehicleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisDriverSupplySourceTests {
    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private GeoOperations<String, String> geoOperations;

    private RedisDriverSupplySource source;

    @BeforeEach
    void setUp() {
        source = new RedisDriverSupplySource(redisTemplate);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    }

    @Test
    void readsAvailableAndBusyDriversAndSkipsExpiredStatusEntries() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForGeo()).thenReturn(geoOperations);
        when(zSetOperations.range("drivers:online", 0, -1))
                .thenReturn(Set.of("10", "11", "12"));
        when(valueOperations.get("driver:10:status")).thenReturn("AVAILABLE");
        when(valueOperations.get("driver:11:status")).thenReturn("BUSY");
        when(valueOperations.get("driver:12:status")).thenReturn(null);
        when(hashOperations.entries("driver:10:meta"))
                .thenReturn(Map.of("vehicleType", "MOTORBIKE"));
        when(hashOperations.entries("driver:11:meta"))
                .thenReturn(Map.of("vehicleType", "CAR_4_SEAT"));
        when(geoOperations.position("drivers:online", "10"))
                .thenReturn(List.of(new Point(106.70, 10.77)));
        when(geoOperations.position("drivers:online", "11"))
                .thenReturn(List.of(new Point(106.71, 10.78)));

        var result = source.readCurrentSupply();

        assertThat(result.complete()).isTrue();
        assertThat(result.skippedStaleDrivers()).isEqualTo(1);
        assertThat(result.malformedActiveDrivers()).isZero();
        assertThat(result.observations())
                .extracting(
                        observation -> observation.driverId(),
                        observation -> observation.vehicleType(),
                        observation -> observation.status()
                )
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(
                                10L,
                                VehicleType.MOTORBIKE,
                                DriverSupplyStatus.AVAILABLE
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                11L,
                                VehicleType.CAR_4_SEAT,
                                DriverSupplyStatus.BUSY
                        )
                );
        assertThat(result.observations())
                .allSatisfy(observation -> assertThat(observation.location().getSRID()).isEqualTo(4326));
    }

    @Test
    void marksSnapshotIncompleteForUnknownActiveStatus() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(zSetOperations.range("drivers:online", 0, -1)).thenReturn(Set.of("10"));
        when(valueOperations.get("driver:10:status")).thenReturn("PAUSED");

        var result = source.readCurrentSupply();

        assertThat(result.complete()).isFalse();
        assertThat(result.observations()).isEmpty();
        assertThat(result.skippedStaleDrivers()).isZero();
        assertThat(result.malformedActiveDrivers()).isEqualTo(1);
    }
}
