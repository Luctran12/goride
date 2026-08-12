package com.example.goride.chat.dto;

import jakarta.validation.constraints.NotNull;

public record TripMessageReadRequest(@NotNull Long lastReadMessageId) {
}
