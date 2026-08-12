package com.example.goride.chat.dto;

import java.util.Map;

public record TripMessageStompErrorResponse(
        String code,
        String message,
        Map<String, Object> details
) {
    public TripMessageStompErrorResponse {
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
