package com.example.goride.chat.dto;

import java.util.UUID;

public record TripMessageStompAckResponse(
        UUID clientMessageId,
        TripMessageResponse message
) {
    public static TripMessageStompAckResponse from(TripMessageResponse message) {
        return new TripMessageStompAckResponse(message.clientMessageId(), message);
    }
}
