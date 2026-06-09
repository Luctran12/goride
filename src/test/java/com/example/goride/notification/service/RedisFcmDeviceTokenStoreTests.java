package com.example.goride.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisFcmDeviceTokenStoreTests {
    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisFcmDeviceTokenStore store;

    @BeforeEach
    void setUp() {
        store = new RedisFcmDeviceTokenStore(redisTemplate);
    }

    @Test
    void saveTokenWritesUserFcmTokenKey() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        store.saveToken(10L, "fcm-token-123");

        verify(valueOperations).set("fcm_token:10", "fcm-token-123");
    }

    @Test
    void deleteTokenRemovesUserFcmTokenKey() {
        store.deleteToken(10L);

        verify(redisTemplate).delete("fcm_token:10");
    }

    @Test
    void findTokenReadsUserFcmTokenKey() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("fcm_token:10")).thenReturn("fcm-token-123");

        var token = store.findToken(10L);

        assertThat(token).contains("fcm-token-123");
    }
}
