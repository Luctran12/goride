package com.example.goride.user.dto;

import com.example.goride.user.domain.UserRole;
import com.example.goride.user.domain.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UserUpdateRequest(
        @NotBlank
        @Size(max = 100)
        String fullName,

        @NotBlank
        @Size(max = 20)
        String phone,

        @Email
        @Size(max = 120)
        String email,

        @Size(min = 8, max = 72)
        String password,

        @Size(max = 500)
        String avatarUrl,

        @NotNull
        UserStatus status,

        @NotEmpty
        Set<@NotNull UserRole> roles
) {
}
