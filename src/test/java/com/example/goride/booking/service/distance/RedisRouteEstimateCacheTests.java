package com.example.goride.booking.service.distance;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisRouteEstimateCacheTests {
    private static final Location PICKUP =
            new Location(BigDecimal.valueOf(10.77004), BigDecimal.valueOf(106.70004));
    private static final Location DROPOFF =
            new Location(BigDecimal.valueOf(10.81304), BigDecimal.valueOf(106.66504));

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RoutingEstimateCacheProperties properties;
    private RedisRouteEstimateCache cache;

    @BeforeEach
    void setUp() {
        properties = new RoutingEstimateCacheProperties();
        cache = new RedisRouteEstimateCache(redisTemplate, new ObjectMapper(), properties);
    }

    @Test
    void writesJsonWithRoundedCoordinateKeyAndTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        DistanceEstimate estimate = new DistanceEstimate(BigDecimal.valueOf(5.43), 17);

        cache.put("driving", PICKUP, DROPOFF, estimate);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(
                eq("routing:estimate:v1:driving:10.7700:106.7000:10.8130:106.6650"),
                payload.capture(),
                eq(Duration.ofMinutes(5))
        );
        assertThat(payload.getValue()).contains("distanceKm", "5.43", "durationMinutes", "17");
    }

    @Test
    void nearbyCoordinatesResolveToSameRoundedKey() {
        Location nearbyPickup =
                new Location(BigDecimal.valueOf(10.77003), BigDecimal.valueOf(106.70003));
        Location nearbyDropoff =
                new Location(BigDecimal.valueOf(10.81303), BigDecimal.valueOf(106.66503));

        assertThat(cache.cacheKey("driving", nearbyPickup, nearbyDropoff))
                .isEqualTo(cache.cacheKey("driving", PICKUP, DROPOFF));
    }

    @Test
    void returnsValidCachedEstimate() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String key = cache.cacheKey("driving", PICKUP, DROPOFF);
        when(valueOperations.get(key))
                .thenReturn("{\"distanceKm\":5.43,\"durationMinutes\":17}");

        var result = cache.find("driving", PICKUP, DROPOFF);

        assertThat(result).contains(new DistanceEstimate(BigDecimal.valueOf(5.43), 17));
    }

    @Test
    void invalidPayloadIsEvictedAndTreatedAsMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String key = cache.cacheKey("driving", PICKUP, DROPOFF);
        when(valueOperations.get(key)).thenReturn("{\"distanceKm\":null}");

        var result = cache.find("driving", PICKUP, DROPOFF);

        assertThat(result).isEmpty();
        verify(redisTemplate).delete(key);
    }

    @Test
    void redisReadFailureIsTreatedAsMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenThrow(new IllegalStateException("Redis unavailable"));

        var result = cache.find("driving", PICKUP, DROPOFF);

        assertThat(result).isEmpty();
    }

    @Test
    void disabledCacheDoesNotReadOrWriteRedis() {
        properties.setEnabled(false);

        assertThat(cache.find("driving", PICKUP, DROPOFF)).isEmpty();
        cache.put(
                "driving",
                PICKUP,
                DROPOFF,
                new DistanceEstimate(BigDecimal.ONE, 1)
        );

        verify(redisTemplate, never()).opsForValue();
    }
}
