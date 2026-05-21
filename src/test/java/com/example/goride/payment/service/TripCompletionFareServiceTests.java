package com.example.goride.payment.service;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.booking.domain.PricingConfig;
import com.example.goride.booking.domain.Trip;
import com.example.goride.driver.domain.VehicleType;
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

    @BeforeEach
    void setUp() {
        service = new TripCompletionFareService(
                tripLocationHistoryRepository,
                Clock.fixed(COMPLETED_AT, ZoneOffset.UTC)
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
        Trip trip = sampleTrip();
        trip.accept(driver(20L));
        trip.markArrived();
        trip.startTrip();
        ReflectionTestUtils.setField(trip, "startedAt", Instant.parse("2026-05-21T08:00:00Z"));
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
                BigDecimal.valueOf(32200),
                pricingConfig
        );
        ReflectionTestUtils.setField(trip, "id", 99L);
        return trip;
    }

    private TripLocationHistory location(Trip trip, double longitude, double latitude) {
        return TripLocationHistory.record(trip, point(longitude, latitude), null, null);
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
