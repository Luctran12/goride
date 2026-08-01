package com.example.goride.booking.dto;

import com.example.goride.booking.domain.TripStatus;
import com.example.goride.booking.domain.TripStatusHistory;

import java.time.Instant;

public record TripStatusHistoryResponse(
        Long id,
        TripStatus fromStatus,
        TripStatus toStatus,
        Long changedByUserId,
        String note,
        Instant changedAt
) {
    public static TripStatusHistoryResponse from(TripStatusHistory history) {
        Long changedByUserId = history.getChangedBy() == null ? null : history.getChangedBy().getId();
        return new TripStatusHistoryResponse(
                history.getId(),
                history.getFromStatus(),
                history.getToStatus(),
                changedByUserId,
                history.getNote(),
                history.getChangedAt()
        );
    }
}
