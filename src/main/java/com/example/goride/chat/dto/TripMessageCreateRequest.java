package com.example.goride.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TripMessageCreateRequest(
        @NotBlank @Size(max = 1000) String body
) {
}
