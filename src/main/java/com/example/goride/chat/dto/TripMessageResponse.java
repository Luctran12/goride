package com.example.goride.chat.dto;

import com.example.goride.chat.domain.TripMessage;
import com.example.goride.chat.domain.TripMessageSenderRole;

import java.time.Instant;
import java.util.UUID;

public record TripMessageResponse(
        Long id,
        Long tripId,
        Long senderId,
        TripMessageSenderRole senderRole,
        UUID clientMessageId,
        String body,
        Instant sentAt
) {
    public static TripMessageResponse from(TripMessage message) {
        return new TripMessageResponse(
                message.getId(),
                message.getTrip().getId(),
                message.getSender().getId(),
                message.getSenderRole(),
                message.getClientMessageId(),
                message.getBody(),
                message.getSentAt()
        );
    }
}
