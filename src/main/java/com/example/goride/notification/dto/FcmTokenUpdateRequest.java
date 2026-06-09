package com.example.goride.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FcmTokenUpdateRequest(
        @NotBlank
        @Size(max = 4096)
        String token
) {
}
