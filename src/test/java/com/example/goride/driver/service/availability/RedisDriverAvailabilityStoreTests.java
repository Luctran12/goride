package com.example.goride.driver.service.availability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    private RedisDriverAvailabilityStore store;

    @BeforeEach
    void setUp() {
        store = new RedisDriverAvailabilityStore(redisTemplate);
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
}
