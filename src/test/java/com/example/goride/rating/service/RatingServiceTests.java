package com.example.goride.rating.service;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.booking.domain.PricingConfig;
import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.driver.domain.DriverProfile;
import com.example.goride.driver.domain.VehicleType;
import com.example.goride.driver.repository.DriverProfileRepository;
import com.example.goride.driver.service.availability.DriverAvailabilityStore;
import com.example.goride.rating.domain.Rating;
import com.example.goride.rating.dto.RatingCreateRequest;
import com.example.goride.rating.repository.RatingRepository;
import com.example.goride.user.domain.User;
import com.example.goride.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RatingServiceTests {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Mock
    private TripRepository tripRepository;

    @Mock
    private RatingRepository ratingRepository;

    @Mock
    private DriverProfileRepository driverProfileRepository;

    @Mock
    private DriverAvailabilityStore driverAvailabilityStore;

    private RatingService service;

    @BeforeEach
    void setUp() {
        service = new RatingService(tripRepository, ratingRepository, driverProfileRepository, driverAvailabilityStore);
    }

    @Test
    void createsRatingForCompletedTripAndUpdatesDriverAverage() {
        User driver = driver(20L);
        Trip trip = completedTrip(driver);
        DriverProfile driverProfile = driverProfile(driver);
        driverProfile.updateAverageRating(BigDecimal.valueOf(4.0), 2);
        when(tripRepository.findActiveByIdForUpdate(99L)).thenReturn(Optional.of(trip));
        when(ratingRepository.existsByTripId(99L)).thenReturn(false);
        when(driverProfileRepository.findByUserIdForUpdate(20L)).thenReturn(Optional.of(driverProfile));
        when(ratingRepository.save(any(Rating.class))).thenAnswer(invocation -> {
            Rating rating = invocation.getArgument(0);
            ReflectionTestUtils.setField(rating, "id", 55L);
            ReflectionTestUtils.setField(rating, "createdAt", Instant.parse("2026-05-25T02:00:00Z"));
            return rating;
        });

        var response = service.createRating(10L, new RatingCreateRequest(99L, 5, "  Great driver  "));

        ArgumentCaptor<Rating> ratingCaptor = ArgumentCaptor.forClass(Rating.class);
        verify(ratingRepository).save(ratingCaptor.capture());
        verify(driverProfileRepository).save(driverProfile);
        verify(driverAvailabilityStore).updateRating(20L, BigDecimal.valueOf(4.3));
        assertThat(ratingCaptor.getValue().getPassenger().getId()).isEqualTo(10L);
        assertThat(ratingCaptor.getValue().getDriver().getId()).isEqualTo(20L);
        assertThat(ratingCaptor.getValue().getScore()).isEqualTo(5);
        assertThat(ratingCaptor.getValue().getComment()).isEqualTo("Great driver");
        assertThat(driverProfile.getAverageRating()).isEqualByComparingTo(BigDecimal.valueOf(4.3));
        assertThat(driverProfile.getTotalRatings()).isEqualTo(3);
        assertThat(response.ratingId()).isEqualTo(55L);
        assertThat(response.tripId()).isEqualTo(99L);
        assertThat(response.driverId()).isEqualTo(20L);
        assertThat(response.score()).isEqualTo(5);
        assertThat(response.comment()).isEqualTo("Great driver");
    }

    @Test
    void rejectsPassengerThatDoesNotOwnTrip() {
        Trip trip = completedTrip(driver(20L));
        when(tripRepository.findActiveByIdForUpdate(99L)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> service.createRating(11L, new RatingCreateRequest(99L, 5, null)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN)
                );

        verifyNoInteractions(ratingRepository, driverProfileRepository, driverAvailabilityStore);
    }

    @Test
    void rejectsTripBeforeCompleted() {
        Trip trip = acceptedTrip(driver(20L));
        when(tripRepository.findActiveByIdForUpdate(99L)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> service.createRating(10L, new RatingCreateRequest(99L, 5, null)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.TRIP_STATUS_INVALID_TRANSITION)
                );

        verifyNoInteractions(ratingRepository, driverProfileRepository, driverAvailabilityStore);
    }

    @Test
    void rejectsDuplicateTripRating() {
        Trip trip = completedTrip(driver(20L));
        when(tripRepository.findActiveByIdForUpdate(99L)).thenReturn(Optional.of(trip));
        when(ratingRepository.existsByTripId(99L)).thenReturn(true);

        assertThatThrownBy(() -> service.createRating(10L, new RatingCreateRequest(99L, 5, null)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.TRIP_ALREADY_RATED)
                );

        verifyNoInteractions(driverProfileRepository, driverAvailabilityStore);
        verify(ratingRepository, never()).save(any());
    }

    @Test
    void rejectsInvalidScoreBeforeLoadingTrip() {
        assertThatThrownBy(() -> service.createRating(10L, new RatingCreateRequest(99L, 0, null)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR)
                );

        verifyNoInteractions(tripRepository, ratingRepository, driverProfileRepository, driverAvailabilityStore);
    }

    @Test
    void rejectsMissingDriverProfile() {
        Trip trip = completedTrip(driver(20L));
        when(tripRepository.findActiveByIdForUpdate(99L)).thenReturn(Optional.of(trip));
        when(ratingRepository.existsByTripId(99L)).thenReturn(false);
        when(driverProfileRepository.findByUserIdForUpdate(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createRating(10L, new RatingCreateRequest(99L, 5, null)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.DRIVER_PROFILE_NOT_FOUND)
                );

        verify(ratingRepository, never()).save(any());
        verify(driverProfileRepository, never()).save(any());
        verifyNoInteractions(driverAvailabilityStore);
    }

    @Test
    void listsDriverRatingsWithOneBasedPagination() {
        User driver = driver(20L);
        DriverProfile driverProfile = driverProfile(driver);
        Rating firstRating = savedRating(completedTrip(driver), 55L, 5, "Great driver", "2026-05-25T02:00:00Z");
        Rating secondRating = savedRating(completedTrip(driver), 54L, 4, null, "2026-05-24T02:00:00Z");
        when(driverProfileRepository.findByUserIdAndUserDeletedAtIsNull(20L)).thenReturn(Optional.of(driverProfile));
        when(ratingRepository.findByDriverIdOrderByCreatedAtDesc(20L, PageRequest.of(0, 2)))
                .thenReturn(new PageImpl<>(List.of(firstRating, secondRating), PageRequest.of(0, 2), 3));

        var response = service.listDriverRatings(20L, 1, 2);

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).ratingId()).isEqualTo(55L);
        assertThat(response.items().get(0).score()).isEqualTo(5);
        assertThat(response.items().get(0).comment()).isEqualTo("Great driver");
        assertThat(response.items().get(1).ratingId()).isEqualTo(54L);
        assertThat(response.pagination().page()).isEqualTo(1);
        assertThat(response.pagination().size()).isEqualTo(2);
        assertThat(response.pagination().totalItems()).isEqualTo(3);
        assertThat(response.pagination().totalPages()).isEqualTo(2);
    }

    @Test
    void listDriverRatingsRejectsMissingDriverProfile() {
        when(driverProfileRepository.findByUserIdAndUserDeletedAtIsNull(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listDriverRatings(20L, 1, 20))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.DRIVER_PROFILE_NOT_FOUND)
                );

        verifyNoInteractions(ratingRepository);
    }

    @Test
    void listDriverRatingsRejectsInvalidPaginationBeforeQuerying() {
        assertThatThrownBy(() -> service.listDriverRatings(20L, 0, 20))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR)
                );
        assertThatThrownBy(() -> service.listDriverRatings(20L, 1, 101))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR)
                );

        verifyNoInteractions(driverProfileRepository, ratingRepository);
    }

    @Test
    void returnsRatedStatusForPassengerTrip() {
        Trip trip = completedTrip(driver(20L));
        Rating rating = savedRating(trip, 55L, 5, "Great driver", "2026-05-25T02:00:00Z");
        when(tripRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.of(trip));
        when(ratingRepository.findByTripId(99L)).thenReturn(Optional.of(rating));

        var response = service.getMyTripRatingStatus(10L, 99L);

        assertThat(response.tripId()).isEqualTo(99L);
        assertThat(response.rated()).isTrue();
        assertThat(response.rating().ratingId()).isEqualTo(55L);
        assertThat(response.rating().score()).isEqualTo(5);
    }

    @Test
    void returnsNotRatedStatusForPassengerTripWithoutRating() {
        Trip trip = completedTrip(driver(20L));
        when(tripRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.of(trip));
        when(ratingRepository.findByTripId(99L)).thenReturn(Optional.empty());

        var response = service.getMyTripRatingStatus(10L, 99L);

        assertThat(response.tripId()).isEqualTo(99L);
        assertThat(response.rated()).isFalse();
        assertThat(response.rating()).isNull();
    }

    @Test
    void tripRatingStatusRejectsUnrelatedPassenger() {
        Trip trip = completedTrip(driver(20L));
        when(tripRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> service.getMyTripRatingStatus(11L, 99L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN)
                );

        verify(ratingRepository, never()).findByTripId(any());
    }

    @Test
    void tripRatingStatusRejectsMissingTrip() {
        when(tripRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyTripRatingStatus(10L, 99L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.TRIP_NOT_FOUND)
                );

        verifyNoInteractions(ratingRepository);
    }

    private Trip completedTrip(User driver) {
        Trip trip = acceptedTrip(driver);
        trip.markArrived();
        trip.startTrip();
        trip.complete(BigDecimal.valueOf(20000), BigDecimal.ONE, 20);
        return trip;
    }

    private Trip acceptedTrip(User driver) {
        Trip trip = sampleTrip();
        trip.accept(driver);
        return trip;
    }

    private Trip sampleTrip() {
        PricingConfig pricingConfig = PricingConfig.create(
                VehicleType.MOTORBIKE,
                BigDecimal.valueOf(10000),
                BigDecimal.valueOf(4000),
                BigDecimal.valueOf(300),
                BigDecimal.valueOf(15000),
                BigDecimal.ONE,
                Instant.parse("2026-01-01T00:00:00Z")
        );
        Trip trip = Trip.create(
                passenger(10L),
                VehicleType.MOTORBIKE,
                PaymentMethod.CASH,
                "Ben Thanh Market",
                point(106.7000, 10.7700),
                "Tan Son Nhat Airport",
                point(106.6650, 10.8130),
                BigDecimal.valueOf(4.2),
                18,
                BigDecimal.valueOf(32000),
                pricingConfig
        );
        ReflectionTestUtils.setField(trip, "id", 99L);
        return trip;
    }

    private DriverProfile driverProfile(User driver) {
        return DriverProfile.create(
                driver,
                "LICENSE-1",
                LocalDate.now().plusYears(2),
                "ID-CARD-1",
                "https://example.com/portrait.jpg",
                "59A1-12345",
                VehicleType.MOTORBIKE,
                "Honda",
                "Wave",
                "Black",
                (short) 2022
        );
    }

    private Rating savedRating(Trip trip, Long id, int score, String comment, String createdAt) {
        Rating rating = Rating.create(trip, trip.getPassenger(), trip.getDriver(), score, comment);
        ReflectionTestUtils.setField(rating, "id", id);
        ReflectionTestUtils.setField(rating, "createdAt", Instant.parse(createdAt));
        return rating;
    }

    private User passenger(Long id) {
        User user = User.create("Passenger", "0900000000", null, "hash", Set.of(UserRole.PASSENGER));
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private User driver(Long id) {
        User user = User.create("Driver", "0900000001", null, "hash", Set.of(UserRole.DRIVER));
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static org.locationtech.jts.geom.Point point(double longitude, double latitude) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
    }
}
