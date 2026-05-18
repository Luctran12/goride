package com.example.goride.auth.dto;

import com.example.goride.user.domain.UserRole;

import java.util.Set;

public record AuthResponse(
        Long userId,
        String accessToken,
        String refreshToken,
        Set<UserRole> roles
) {
}
