package com.example.goride.payment.service;

import com.example.goride.booking.domain.Trip;
import com.example.goride.tracking.domain.TripLocationHistory;
import com.example.goride.tracking.repository.TripLocationHistoryRepository;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
public class TripCompletionFareService {
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private final TripLocationHistoryRepository tripLocationHistoryRepository;
    private final Clock clock;

    public TripCompletionFareService(
            TripLocationHistoryRepository tripLocationHistoryRepository,
            Clock clock
    ) {
        this.tripLocationHistoryRepository = tripLocationHistoryRepository;
        this.clock = clock;
    }

    public TripCompletionFare calculate(Trip trip) {
        BigDecimal actualDistanceKm = trackedDistanceKm(trip)
                .filter(distance -> distance.signum() > 0)
                .orElse(trip.getEstimatedDistanceKm());
        int actualDurationMin = actualDurationMin(trip);
        BigDecimal finalFare = trip.getPricingConfig().estimateFare(actualDistanceKm, actualDurationMin);
        return new TripCompletionFare(finalFare, actualDistanceKm, actualDurationMin);
    }

    private Optional<BigDecimal> trackedDistanceKm(Trip trip) {
        List<TripLocationHistory> history = tripLocationHistoryRepository
                .findByTripIdAndTripDeletedAtIsNullOrderByRecordedAtAsc(trip.getId());
        if (history.size() < 2) {
            return Optional.empty();
        }

        double totalMeters = 0.0;
        Point previousPoint = history.get(0).getLocation();
        for (int index = 1; index < history.size(); index++) {
            Point nextPoint = history.get(index).getLocation();
            totalMeters += haversineMeters(previousPoint, nextPoint);
            previousPoint = nextPoint;
        }

        return Optional.of(BigDecimal.valueOf(totalMeters / 1000.0).setScale(2, RoundingMode.HALF_UP));
    }

    private int actualDurationMin(Trip trip) {
        if (trip.getStartedAt() == null) {
            return trip.getEstimatedDurationMin();
        }

        long seconds = Duration.between(trip.getStartedAt(), clock.instant()).getSeconds();
        if (seconds <= 0) {
            return 1;
        }
        long roundedMinutes = (seconds + 59) / 60;
        return roundedMinutes > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) roundedMinutes;
    }

    private double haversineMeters(Point from, Point to) {
        double fromLatitude = Math.toRadians(from.getY());
        double toLatitude = Math.toRadians(to.getY());
        double latitudeDelta = Math.toRadians(to.getY() - from.getY());
        double longitudeDelta = Math.toRadians(to.getX() - from.getX());

        double angle = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(fromLatitude) * Math.cos(toLatitude)
                * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        double normalizedAngle = Math.min(1.0, Math.max(0.0, angle));
        double centralAngle = 2 * Math.atan2(Math.sqrt(normalizedAngle), Math.sqrt(1 - normalizedAngle));
        return EARTH_RADIUS_METERS * centralAngle;
    }
}
