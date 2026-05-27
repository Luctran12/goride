package com.example.goride.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserPasswordChangeRequest(
        @NotBlank
        @Size(max = 72)
        String currentPassword,

        @NotBlank
        @Size(min = 8, max = 72)
        String newPassword
) {
}
