package com.example.goride.tracking.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisLatestDriverLocationStoreTests {
    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private RedisLatestDriverLocationStore store;

    @BeforeEach
    void setUp() {
        store = new RedisLatestDriverLocationStore(redisTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveWritesLatestLocationWithTtl() {
        Instant updatedAt = Instant.parse("2026-05-21T08:00:00Z");
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        store.save(new LatestDriverLocationStore.LatestDriverLocation(
                99L,
                20L,
                BigDecimal.valueOf(10.78),
                BigDecimal.valueOf(106.69),
                BigDecimal.valueOf(92.5),
                BigDecimal.valueOf(28.4),
                updatedAt
        ));

        ArgumentCaptor<Map<Object, Object>> fieldsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(hashOperations).putAll(eq("driver:20:location"), fieldsCaptor.capture());
        verify(redisTemplate).expire("driver:20:location", Duration.ofSeconds(30));
        assertThat(fieldsCaptor.getValue())
                .containsEntry("tripId", "99")
                .containsEntry("driverId", "20")
                .containsEntry("lat", "10.78")
                .containsEntry("lng", "106.69")
                .containsEntry("bearing", "92.5")
                .containsEntry("speed", "28.4")
                .containsEntry("updatedAt", "2026-05-21T08:00:00Z");
    }

    @Test
    void findByDriverIdParsesStoredLocation() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("driver:20:location")).thenReturn(Map.of(
                "tripId", "99",
                "driverId", "20",
                "lat", "10.78",
                "lng", "106.69",
                "bearing", "92.5",
                "speed", "28.4",
                "updatedAt", "2026-05-21T08:00:00Z"
        ));

        var location = store.findByDriverId(20L);

        assertThat(location).isPresent();
        assertThat(location.get().tripId()).isEqualTo(99L);
        assertThat(location.get().driverId()).isEqualTo(20L);
        assertThat(location.get().lat()).isEqualByComparingTo("10.78");
        assertThat(location.get().lng()).isEqualByComparingTo("106.69");
        assertThat(location.get().bearing()).isEqualByComparingTo("92.5");
        assertThat(location.get().speed()).isEqualByComparingTo("28.4");
        assertThat(location.get().updatedAt()).isEqualTo(Instant.parse("2026-05-21T08:00:00Z"));
    }

    @Test
    void findByDriverIdReturnsEmptyWhenCacheIsMissing() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("driver:20:location")).thenReturn(Map.of());

        assertThat(store.findByDriverId(20L)).isEmpty();
    }
}
