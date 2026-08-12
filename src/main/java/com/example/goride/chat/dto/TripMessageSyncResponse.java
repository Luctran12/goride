package com.example.goride.chat.dto;

import java.util.List;

public record TripMessageSyncResponse(
        List<TripMessageResponse> items,
        TripMessageSyncMode mode,
        boolean hasMore,
        Long nextCursor
) {
    public TripMessageSyncResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
