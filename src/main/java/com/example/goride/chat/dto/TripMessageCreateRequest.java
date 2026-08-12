package com.example.goride.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record TripMessageCreateRequest(
        @NotNull UUID clientMessageId,
        @NotBlank @Size(max = 1000) String body
) {
}
