package com.example.goride.notification.dto;

public record FcmTokenResponse(
        Long userId,
        boolean registered
) {
    public static FcmTokenResponse registered(Long userId) {
        return new FcmTokenResponse(userId, true);
    }

    public static FcmTokenResponse deleted(Long userId) {
        return new FcmTokenResponse(userId, false);
    }
}
