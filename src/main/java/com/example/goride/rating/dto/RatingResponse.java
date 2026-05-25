package com.example.goride.rating.dto;

import com.example.goride.rating.domain.Rating;

import java.time.Instant;

public record RatingResponse(
        Long ratingId,
        Long tripId,
        Long driverId,
        int score,
        String comment,
        Instant createdAt
) {
    public static RatingResponse from(Rating rating) {
        return new RatingResponse(
                rating.getId(),
                rating.getTrip().getId(),
                rating.getDriver().getId(),
                rating.getScore(),
                rating.getComment(),
                rating.getCreatedAt()
        );
    }
}
