package com.example.goride.user.dto;

import com.example.goride.user.domain.UserRole;
import com.example.goride.user.domain.UserStatus;

import java.time.Instant;
import java.util.Set;

public record UserResponse(
        Long id,
        String fullName,
        String phone,
        String email,
        String avatarUrl,
        UserStatus status,
        Set<UserRole> roles,
        Instant createdAt,
        Instant updatedAt
) {
}
