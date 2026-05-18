package com.example.goride.auth.service;

import com.example.goride.auth.config.JwtProperties;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.user.domain.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

@Service
public class RefreshTokenService {
    private static final String KEY_PREFIX = "refresh_token:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(StringRedisTemplate redisTemplate, JwtProperties jwtProperties) {
        this.redisTemplate = redisTemplate;
        this.jwtProperties = jwtProperties;
    }

    public String createRefreshToken(User user) {
        String tokenId = UUID.randomUUID().toString();
        String secret = randomSecret();
        String token = user.getId() + "." + tokenId + "." + secret;
        redisTemplate.opsForValue().set(
                key(user.getId(), tokenId),
                sha256(secret),
                Duration.ofDays(jwtProperties.refreshTokenDays())
        );
        return token;
    }

    public RefreshTokenClaims validate(String refreshToken) {
        ParsedRefreshToken parsed = parse(refreshToken);
        String storedHash = redisTemplate.opsForValue().get(key(parsed.userId(), parsed.tokenId()));
        if (storedHash == null || !MessageDigest.isEqual(storedHash.getBytes(StandardCharsets.UTF_8), sha256(parsed.secret()).getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }
        return new RefreshTokenClaims(parsed.userId(), parsed.tokenId());
    }

    public void revoke(String refreshToken) {
        ParsedRefreshToken parsed = parse(refreshToken);
        redisTemplate.delete(key(parsed.userId(), parsed.tokenId()));
    }

    private ParsedRefreshToken parse(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        String[] parts = refreshToken.split("\\.", 3);
        if (parts.length != 3) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        try {
            return new ParsedRefreshToken(Long.parseLong(parts[0]), parts[1], parts[2]);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
    }

    private String key(Long userId, String tokenId) {
        return KEY_PREFIX + userId + ":" + tokenId;
    }

    private String randomSecret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public record RefreshTokenClaims(Long userId, String tokenId) {
    }

    private record ParsedRefreshToken(Long userId, String tokenId, String secret) {
    }
}
