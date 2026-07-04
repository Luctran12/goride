package com.example.goride.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TripMessageSendRequest(
        @NotNull Long tripId,
        @NotBlank @Size(max = 1000) String body
) {
}
