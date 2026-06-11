package com.example.goride.rating.service;

import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.domain.TripStatus;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.common.api.PageResponse;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.driver.domain.DriverProfile;
import com.example.goride.driver.repository.DriverProfileRepository;
import com.example.goride.driver.service.availability.DriverAvailabilityStore;
import com.example.goride.rating.domain.Rating;
import com.example.goride.rating.dto.RatingCreateRequest;
import com.example.goride.rating.dto.RatingResponse;
import com.example.goride.rating.dto.TripRatingStatusResponse;
import com.example.goride.rating.repository.RatingRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Service
public class RatingService {
    private static final int MAX_PAGE_SIZE = 100;

    private final TripRepository tripRepository;
    private final RatingRepository ratingRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final DriverAvailabilityStore driverAvailabilityStore;

    public RatingService(
            TripRepository tripRepository,
            RatingRepository ratingRepository,
            DriverProfileRepository driverProfileRepository,
            DriverAvailabilityStore driverAvailabilityStore
    ) {
        this.tripRepository = tripRepository;
        this.ratingRepository = ratingRepository;
        this.driverProfileRepository = driverProfileRepository;
        this.driverAvailabilityStore = driverAvailabilityStore;
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
        syncDriverRatingAfterCommit(trip.getDriver().getId(), driverProfile.getAverageRating());
        return RatingResponse.from(savedRating);
    }

    @Transactional(readOnly = true)
    public PageResponse<RatingResponse> listDriverRatings(Long driverId, int page, int size) {
        validatePageRequest(driverId, page, size);
        driverProfileRepository.findByUserIdAndUserDeletedAtIsNull(driverId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DRIVER_PROFILE_NOT_FOUND));

        Page<Rating> ratings = ratingRepository.findByDriverIdOrderByCreatedAtDesc(
                driverId,
                PageRequest.of(page - 1, size)
        );
        return PageResponse.of(
                ratings.getContent().stream()
                        .map(RatingResponse::from)
                        .toList(),
                page,
                size,
                ratings.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public TripRatingStatusResponse getMyTripRatingStatus(Long passengerId, Long tripId) {
        if (tripId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Trip id is required");
        }
        Trip trip = tripRepository.findByIdAndDeletedAtIsNull(tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
        assertPassengerOwnsTrip(passengerId, trip);
        return ratingRepository.findByTripId(trip.getId())
                .map(RatingResponse::from)
                .map(TripRatingStatusResponse::rated)
                .orElseGet(() -> TripRatingStatusResponse.notRated(trip.getId()));
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

    private void validatePageRequest(Long driverId, int page, int size) {
        if (driverId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Driver id is required");
        }
        if (page < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Page must be greater than or equal to 1");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Size must be between 1 and 100");
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

    private void syncDriverRatingAfterCommit(Long driverId, BigDecimal averageRating) {
        runAfterCommit(() -> driverAvailabilityStore.updateRating(driverId, averageRating));
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
