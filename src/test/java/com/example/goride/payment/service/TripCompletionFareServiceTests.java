package com.example.goride.payment.service;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.booking.domain.PricingConfig;
import com.example.goride.booking.domain.Trip;
import com.example.goride.driver.domain.VehicleType;
import com.example.goride.payment.config.FareDistanceFilterProperties;
import com.example.goride.tracking.domain.TripLocationHistory;
import com.example.goride.tracking.repository.TripLocationHistoryRepository;
import com.example.goride.user.domain.User;
import com.example.goride.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripCompletionFareServiceTests {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final Instant COMPLETED_AT = Instant.parse("2026-05-21T08:20:00Z");

    @Mock
    private TripLocationHistoryRepository tripLocationHistoryRepository;

    private TripCompletionFareService service;
    private FareDistanceFilterProperties filterProperties;
    private Instant nextLocationTime;

    @BeforeEach
    void setUp() {
        filterProperties = new FareDistanceFilterProperties();
        nextLocationTime = Instant.parse("2026-05-21T08:00:00Z");
        service = new TripCompletionFareService(
                tripLocationHistoryRepository,
                Clock.fixed(COMPLETED_AT, ZoneOffset.UTC),
                filterProperties
        );
    }

    @Test
    void calculatesFareFromTrackedDistanceAndActualDuration() {
        Trip trip = inProgressTrip();
        when(tripLocationHistoryRepository.findByTripIdAndTripDeletedAtIsNullOrderByRecordedAtAsc(99L))
                .thenReturn(List.of(
                        location(trip, 106.7000, 10.7700),
                        location(trip, 106.7000, 10.7790)
                ));

        TripCompletionFare fare = service.calculate(trip);

        assertThat(fare.actualDistanceKm()).isEqualByComparingTo("1.00");
        assertThat(fare.actualDurationMin()).isEqualTo(20);
        assertThat(fare.finalFare()).isEqualByComparingTo("20000");
    }

    @Test
    void appliesTripSurgeSnapshotWhenCompletingFare() {
        Trip trip = inProgressTrip(BigDecimal.valueOf(1.25));
        when(tripLocationHistoryRepository.findByTripIdAndTripDeletedAtIsNullOrderByRecordedAtAsc(99L))
                .thenReturn(List.of(
                        location(trip, 106.7000, 10.7700),
                        location(trip, 106.7000, 10.7790)
                ));

        TripCompletionFare fare = service.calculate(trip);

        assertThat(fare.actualDistanceKm()).isEqualByComparingTo("1.00");
        assertThat(fare.actualDurationMin()).isEqualTo(20);
        assertThat(fare.finalFare()).isEqualByComparingTo("25000");
    }

    @Test
    void fallsBackToEstimatedDistanceWhenTrackingHistoryIsInsufficient() {
        Trip trip = inProgressTrip();
        ReflectionTestUtils.setField(trip, "startedAt", Instant.parse("2026-05-21T08:02:00Z"));
        when(tripLocationHistoryRepository.findByTripIdAndTripDeletedAtIsNullOrderByRecordedAtAsc(99L))
                .thenReturn(List.of(location(trip, 106.7000, 10.7700)));

        TripCompletionFare fare = service.calculate(trip);

        assertThat(fare.actualDistanceKm()).isEqualByComparingTo("4.20");
        assertThat(fare.actualDurationMin()).isEqualTo(18);
        assertThat(fare.finalFare()).isEqualByComparingTo("32200");
    }

    @Test
    void ignoresGpsJitterBeforeAddingValidMovement() {
        Trip trip = inProgressTrip();
        Instant startedAt = Instant.parse("2026-05-21T08:00:00Z");
        when(tripLocationHistoryRepository.findByTripIdAndTripDeletedAtIsNullOrderByRecordedAtAsc(99L))
                .thenReturn(List.of(
                        locationAt(trip, 106.7000, 10.7700, startedAt),
                        locationAt(trip, 106.7000, 10.77001, startedAt.plusSeconds(5)),
                        locationAt(trip, 106.7000, 10.7745, startedAt.plusSeconds(10)),
                        locationAt(trip, 106.7000, 10.7790, startedAt.plusSeconds(20))
                ));

        TripCompletionFare fare = service.calculate(trip);

        assertThat(fare.actualDistanceKm()).isEqualByComparingTo("1.00");
    }

    @Test
    void rejectsTeleportSpikeWithoutUsingItAsNextAnchor() {
        Trip trip = inProgressTrip();
        Instant startedAt = Instant.parse("2026-05-21T08:00:00Z");
        when(tripLocationHistoryRepository.findByTripIdAndTripDeletedAtIsNullOrderByRecordedAtAsc(99L))
                .thenReturn(List.of(
                        locationAt(trip, 106.7000, 10.7700, startedAt),
                        locationAt(trip, 106.7000, 10.8000, startedAt.plusSeconds(5)),
                        locationAt(trip, 106.7000, 10.7745, startedAt.plusSeconds(10)),
                        locationAt(trip, 106.7000, 10.7790, startedAt.plusSeconds(20))
                ));

        TripCompletionFare fare = service.calculate(trip);

        assertThat(fare.actualDistanceKm()).isEqualByComparingTo("1.00");
    }

    @Test
    void rebasesAfterLongTrackingGapAndCountsLaterValidSegments() {
        Trip trip = inProgressTrip();
        Instant startedAt = Instant.parse("2026-05-21T08:00:00Z");
        when(tripLocationHistoryRepository.findByTripIdAndTripDeletedAtIsNullOrderByRecordedAtAsc(99L))
                .thenReturn(List.of(
                        locationAt(trip, 106.7000, 10.7700, startedAt),
                        locationAt(trip, 106.7000, 10.7790, startedAt.plusSeconds(60)),
                        locationAt(trip, 106.7000, 10.7880, startedAt.plusSeconds(80))
                ));

        TripCompletionFare fare = service.calculate(trip);

        assertThat(fare.actualDistanceKm()).isEqualByComparingTo("1.00");
    }

    @Test
    void ignoresNonIncreasingTimestampWithoutChangingAnchor() {
        Trip trip = inProgressTrip();
        Instant startedAt = Instant.parse("2026-05-21T08:00:00Z");
        when(tripLocationHistoryRepository.findByTripIdAndTripDeletedAtIsNullOrderByRecordedAtAsc(99L))
                .thenReturn(List.of(
                        locationAt(trip, 106.7000, 10.7700, startedAt),
                        locationAt(trip, 106.7000, 10.7900, startedAt),
                        locationAt(trip, 106.7000, 10.7745, startedAt.plusSeconds(10))
                ));

        TripCompletionFare fare = service.calculate(trip);

        assertThat(fare.actualDistanceKm()).isEqualByComparingTo("0.50");
    }

    @Test
    void fallsBackToEstimateWhenAllTrackedSegmentsAreRejected() {
        Trip trip = inProgressTrip();
        Instant startedAt = Instant.parse("2026-05-21T08:00:00Z");
        when(tripLocationHistoryRepository.findByTripIdAndTripDeletedAtIsNullOrderByRecordedAtAsc(99L))
                .thenReturn(List.of(
                        locationAt(trip, 106.7000, 10.7700, startedAt),
                        locationAt(trip, 106.7000, 10.7790, startedAt.plusSeconds(5))
                ));

        TripCompletionFare fare = service.calculate(trip);

        assertThat(fare.actualDistanceKm()).isEqualByComparingTo("4.20");
    }

    @Test
    void preservesLegacyDistanceCalculationWhenFilterIsDisabled() {
        filterProperties.setEnabled(false);
        Trip trip = inProgressTrip();
        Instant startedAt = Instant.parse("2026-05-21T08:00:00Z");
        when(tripLocationHistoryRepository.findByTripIdAndTripDeletedAtIsNullOrderByRecordedAtAsc(99L))
                .thenReturn(List.of(
                        locationAt(trip, 106.7000, 10.7700, startedAt),
                        locationAt(trip, 106.7000, 10.7790, startedAt.plusSeconds(1))
                ));

        TripCompletionFare fare = service.calculate(trip);

        assertThat(fare.actualDistanceKm()).isEqualByComparingTo("1.00");
    }

    @Test
    void roundsPartialDurationUpToNextMinute() {
        Trip trip = inProgressTrip();
        ReflectionTestUtils.setField(trip, "startedAt", Instant.parse("2026-05-21T08:18:30Z"));
        when(tripLocationHistoryRepository.findByTripIdAndTripDeletedAtIsNullOrderByRecordedAtAsc(99L))
                .thenReturn(List.of(
                        location(trip, 106.7000, 10.7700),
                        location(trip, 106.7000, 10.7790)
                ));

        TripCompletionFare fare = service.calculate(trip);

        assertThat(fare.actualDurationMin()).isEqualTo(2);
        assertThat(fare.finalFare()).isEqualByComparingTo("15000");
    }

    private Trip inProgressTrip() {
        return inProgressTrip(BigDecimal.ONE);
    }

    private Trip inProgressTrip(BigDecimal fareSurgeMultiplier) {
        Trip trip = sampleTrip(fareSurgeMultiplier);
        trip.accept(driver(20L));
        trip.markArrived();
        trip.startTrip();
        ReflectionTestUtils.setField(trip, "startedAt", Instant.parse("2026-05-21T08:00:00Z"));
        return trip;
    }

    private Trip sampleTrip() {
        return sampleTrip(BigDecimal.ONE);
    }

    private Trip sampleTrip(BigDecimal fareSurgeMultiplier) {
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
                BigDecimal.valueOf(32200),
                pricingConfig,
                fareSurgeMultiplier
        );
        ReflectionTestUtils.setField(trip, "id", 99L);
        return trip;
    }

    private TripLocationHistory location(Trip trip, double longitude, double latitude) {
        TripLocationHistory history = locationAt(trip, longitude, latitude, nextLocationTime);
        nextLocationTime = nextLocationTime.plusSeconds(20);
        return history;
    }

    private TripLocationHistory locationAt(
            Trip trip,
            double longitude,
            double latitude,
            Instant recordedAt
    ) {
        TripLocationHistory history = TripLocationHistory.record(trip, point(longitude, latitude), null, null);
        ReflectionTestUtils.setField(history, "recordedAt", recordedAt);
        return history;
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
