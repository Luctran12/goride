package com.example.goride.chat.dto;

public record TripMessageUnreadCountResponse(
        Long tripId,
        Long lastReadMessageId,
        long unreadCount
) {
}
