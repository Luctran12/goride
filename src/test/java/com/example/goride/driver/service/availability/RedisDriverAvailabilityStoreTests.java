package com.example.goride.driver.service.availability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.geo.Point;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisDriverAvailabilityStoreTests {
    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private GeoOperations<String, String> geoOperations;

    private RedisDriverAvailabilityStore store;
    private DriverAvailabilityProperties properties;

    @BeforeEach
    void setUp() {
        properties = new DriverAvailabilityProperties();
        store = new RedisDriverAvailabilityStore(redisTemplate, properties);
    }

    @Test
    void markAvailableWritesLocationMetadataAndAvailableStatusWithHeartbeatTtl() {
        when(redisTemplate.opsForGeo()).thenReturn(geoOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        store.markAvailable(availability());

        verify(geoOperations).add(eq("drivers:online"), any(), eq("20"));
        verify(hashOperations).putAll(eq("driver:20:meta"), any());
        verify(redisTemplate).expire("driver:20:meta", Duration.ofSeconds(60));
        verify(valueOperations).set("driver:20:status", "AVAILABLE", Duration.ofSeconds(60));
    }

    @Test
    void refreshHeartbeatPreservesBusyStatusAndRefreshesTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("driver:20:status")).thenReturn("BUSY");
        when(redisTemplate.opsForGeo()).thenReturn(geoOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.expire(anyString(), eq(Duration.ofSeconds(60)))).thenReturn(true);

        boolean refreshed = store.refreshHeartbeat(availability());

        assertThat(refreshed).isTrue();
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
        verify(redisTemplate).expire("driver:20:meta", Duration.ofSeconds(60));
        verify(redisTemplate).expire("driver:20:status", Duration.ofSeconds(60));
    }

    @Test
    void refreshHeartbeatRejectsExpiredStatusWithoutRecreatingAvailability() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("driver:20:status")).thenReturn(null);

        boolean refreshed = store.refreshHeartbeat(availability());

        assertThat(refreshed).isFalse();
        verify(redisTemplate, never()).opsForGeo();
        verify(redisTemplate, never()).opsForHash();
    }

    @Test
    void findLocationReturnsCurrentOnlineDriverPositionAndTimestamp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("driver:20:status")).thenReturn("BUSY");
        when(redisTemplate.opsForGeo()).thenReturn(geoOperations);
        when(geoOperations.position("drivers:online", "20"))
                .thenReturn(List.of(new Point(106.7009, 10.7769)));
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get("driver:20:meta", "locationUpdatedAt"))
                .thenReturn("2026-07-24T10:15:30Z");

        var location = store.findLocation(20L);

        assertThat(location).contains(new DriverAvailabilityStore.DriverLocation(
                20L,
                BigDecimal.valueOf(10.7769),
                BigDecimal.valueOf(106.7009),
                Instant.parse("2026-07-24T10:15:30Z")
        ));
    }

    @Test
    void findLocationSkipsExpiredDriverStatus() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("driver:20:status")).thenReturn(null);

        assertThat(store.findLocation(20L)).isEmpty();

        verify(redisTemplate, never()).opsForGeo();
        verify(redisTemplate, never()).opsForHash();
    }

    @Test
    void findLocationSkipsLegacyMetadataWithoutTimestamp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("driver:20:status")).thenReturn("AVAILABLE");
        when(redisTemplate.opsForGeo()).thenReturn(geoOperations);
        when(geoOperations.position("drivers:online", "20"))
                .thenReturn(List.of(new Point(106.7009, 10.7769)));
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get("driver:20:meta", "locationUpdatedAt")).thenReturn(null);

        assertThat(store.findLocation(20L)).isEmpty();
    }

    @Test
    void updateRatingWritesRatingForOnlineDriverMetadata() {
        when(redisTemplate.hasKey("driver:20:status")).thenReturn(true);
        when(redisTemplate.hasKey("driver:20:meta")).thenReturn(true);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        store.updateRating(20L, BigDecimal.valueOf(4.3));

        verify(hashOperations).put("driver:20:meta", "rating", "4.3");
        verify(redisTemplate).expire("driver:20:meta", Duration.ofSeconds(60));
    }

    @Test
    void updateRatingSkipsWhenDriverStatusMissing() {
        when(redisTemplate.hasKey("driver:20:status")).thenReturn(false);

        store.updateRating(20L, BigDecimal.valueOf(4.3));

        verify(redisTemplate, never()).opsForHash();
        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
        verifyNoInteractions(hashOperations);
    }

    @Test
    void updateRatingSkipsWhenDriverMetadataMissing() {
        when(redisTemplate.hasKey("driver:20:status")).thenReturn(true);
        when(redisTemplate.hasKey("driver:20:meta")).thenReturn(false);

        store.updateRating(20L, BigDecimal.valueOf(4.3));

        verify(redisTemplate, never()).opsForHash();
        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
        verifyNoInteractions(hashOperations);
    }

    private DriverAvailabilityStore.DriverAvailability availability() {
        return new DriverAvailabilityStore.DriverAvailability(
                20L,
                BigDecimal.valueOf(10.7769),
                BigDecimal.valueOf(106.7009),
                com.example.goride.driver.domain.VehicleType.CAR_4_SEAT,
                BigDecimal.valueOf(4.8),
                "Driver",
                null
        );
    }
}
