package com.example.goride.chat.dto;

import com.example.goride.chat.domain.TripMessageReadState;

import java.time.Instant;

public record TripMessageReadStateResponse(
        Long tripId,
        Long userId,
        Long lastReadMessageId,
        Instant readAt,
        long unreadCount
) {
    public static TripMessageReadStateResponse from(TripMessageReadState state, long unreadCount) {
        return new TripMessageReadStateResponse(
                state.getTrip().getId(),
                state.getUser().getId(),
                state.getLastReadMessage().getId(),
                state.getReadAt(),
                unreadCount
        );
    }
}
