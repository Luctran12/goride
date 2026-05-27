package com.example.goride.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserProfileUpdateRequest(
        @NotBlank
        @Size(max = 100)
        String fullName,

        @NotBlank
        @Size(max = 20)
        String phone,

        @Email
        @Size(max = 120)
        String email,

        @Size(max = 500)
        String avatarUrl
) {
}
