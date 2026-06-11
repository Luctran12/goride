package com.example.goride.auth.dto;

import com.example.goride.user.domain.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record RegisterRequest(
        @NotBlank
        @Size(max = 100)
        String fullName,

        @NotBlank
        @Size(max = 20)
        String phone,

        @Email
        @Size(max = 120)
        String email,

        @NotBlank
        @Size(min = 8, max = 72)
        String password,

        @NotEmpty
        Set<UserRole> roles
) {
}
