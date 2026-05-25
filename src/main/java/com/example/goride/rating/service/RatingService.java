package com.example.goride.rating.service;

import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.domain.TripStatus;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.driver.domain.DriverProfile;
import com.example.goride.driver.repository.DriverProfileRepository;
import com.example.goride.rating.domain.Rating;
import com.example.goride.rating.dto.RatingCreateRequest;
import com.example.goride.rating.dto.RatingResponse;
import com.example.goride.rating.repository.RatingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Service
public class RatingService {
    private final TripRepository tripRepository;
    private final RatingRepository ratingRepository;
    private final DriverProfileRepository driverProfileRepository;

    public RatingService(
            TripRepository tripRepository,
            RatingRepository ratingRepository,
            DriverProfileRepository driverProfileRepository
    ) {
        this.tripRepository = tripRepository;
        this.ratingRepository = ratingRepository;
        this.driverProfileRepository = driverProfileRepository;
    }

    @Transactional
    public RatingResponse createRating(Long passengerId, RatingCreateRequest request) {
        validateRequest(request);
        Trip trip = tripRepository.findActiveByIdForUpdate(request.tripId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
        assertPassengerOwnsTrip(passengerId, trip);
        assertTripCanBeRated(trip);

        if (ratingRepository.existsByTripId(trip.getId())) {
            throw new BusinessException(ErrorCode.TRIP_ALREADY_RATED);
        }

        DriverProfile driverProfile = driverProfileRepository.findByUserIdForUpdate(trip.getDriver().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.DRIVER_PROFILE_NOT_FOUND));
        Rating rating = Rating.create(
                trip,
                trip.getPassenger(),
                trip.getDriver(),
                request.score(),
                request.comment()
        );
        Rating savedRating = ratingRepository.save(rating);
        updateDriverRating(driverProfile, savedRating.getScore());
        driverProfileRepository.save(driverProfile);
        return RatingResponse.from(savedRating);
    }

    private void validateRequest(RatingCreateRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Rating request is required");
        }
        if (request.tripId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Trip id is required");
        }
        if (request.score() == null || request.score() < 1 || request.score() > 5) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Score must be between 1 and 5");
        }
        if (request.comment() != null && request.comment().length() > 500) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Comment must be at most 500 characters");
        }
    }

    private void assertPassengerOwnsTrip(Long passengerId, Trip trip) {
        if (!Objects.equals(trip.getPassenger().getId(), passengerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only the trip passenger can rate this trip");
        }
    }

    private void assertTripCanBeRated(Trip trip) {
        if (trip.getStatus() != TripStatus.COMPLETED || trip.getDriver() == null) {
            throw new BusinessException(
                    ErrorCode.TRIP_STATUS_INVALID_TRANSITION,
                    "Only completed trips can be rated"
            );
        }
    }

    private void updateDriverRating(DriverProfile driverProfile, int score) {
        int currentTotalRatings = driverProfile.getTotalRatings();
        BigDecimal totalScore = driverProfile.getAverageRating()
                .multiply(BigDecimal.valueOf(currentTotalRatings))
                .add(BigDecimal.valueOf(score));
        int newTotalRatings = currentTotalRatings + 1;
        BigDecimal newAverageRating = totalScore.divide(
                BigDecimal.valueOf(newTotalRatings),
                1,
                RoundingMode.HALF_UP
        );
        driverProfile.updateAverageRating(newAverageRating, newTotalRatings);
    }
}
