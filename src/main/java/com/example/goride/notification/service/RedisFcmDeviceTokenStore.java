package com.example.goride.notification.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class RedisFcmDeviceTokenStore implements FcmDeviceTokenStore {
    private static final String KEY_PREFIX = "fcm_token:";

    private final StringRedisTemplate redisTemplate;

    public RedisFcmDeviceTokenStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void saveToken(Long userId, String token) {
        redisTemplate.opsForValue().set(key(userId), token);
    }

    @Override
    public void deleteToken(Long userId) {
        redisTemplate.delete(key(userId));
    }

    @Override
    public Optional<String> findToken(Long userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(userId)));
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
