package com.example.goride.rating.dto;

public record TripRatingStatusResponse(
        Long tripId,
        boolean rated,
        RatingResponse rating
) {
    public static TripRatingStatusResponse notRated(Long tripId) {
        return new TripRatingStatusResponse(tripId, false, null);
    }

    public static TripRatingStatusResponse rated(RatingResponse rating) {
        return new TripRatingStatusResponse(rating.tripId(), true, rating);
    }
}
