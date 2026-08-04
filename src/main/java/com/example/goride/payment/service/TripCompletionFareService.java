package com.example.goride.payment.service;

import com.example.goride.booking.domain.Trip;
import com.example.goride.payment.config.FareDistanceFilterProperties;
import com.example.goride.tracking.domain.TripLocationHistory;
import com.example.goride.tracking.repository.TripLocationHistoryRepository;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
public class TripCompletionFareService {
    private static final Logger log = LoggerFactory.getLogger(TripCompletionFareService.class);
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private final TripLocationHistoryRepository tripLocationHistoryRepository;
    private final Clock clock;
    private final FareDistanceFilterProperties filterProperties;

    public TripCompletionFareService(
            TripLocationHistoryRepository tripLocationHistoryRepository,
            Clock clock,
            FareDistanceFilterProperties filterProperties
    ) {
        this.tripLocationHistoryRepository = tripLocationHistoryRepository;
        this.clock = clock;
        this.filterProperties = filterProperties;
    }

    public TripCompletionFare calculate(Trip trip) {
        BigDecimal actualDistanceKm = trackedDistanceKm(trip)
                .filter(distance -> distance.signum() > 0)
                .orElse(trip.getEstimatedDistanceKm());
        int actualDurationMin = actualDurationMin(trip);
        BigDecimal finalFare = trip.getPricingConfig().estimateFare(
                actualDistanceKm,
                actualDurationMin,
                trip.getFareSurgeMultiplier()
        );
        return new TripCompletionFare(finalFare, actualDistanceKm, actualDurationMin);
    }

    private Optional<BigDecimal> trackedDistanceKm(Trip trip) {
        List<TripLocationHistory> history = tripLocationHistoryRepository
                .findByTripIdAndTripDeletedAtIsNullOrderByRecordedAtAsc(trip.getId());
        if (history.size() < 2) {
            return Optional.empty();
        }

        if (!filterProperties.isEnabled()) {
            return Optional.of(distanceKm(unfilteredDistanceMeters(history)));
        }

        FilteredDistance filteredDistance = filteredDistance(history);
        if (filteredDistance.rejectedSegments() > 0) {
            log.info(
                    "Filtered GPS segments from actual fare distance: tripId={}, acceptedSegments={}, "
                            + "rejectedSegments={}, jitter={}, speed={}, gap={}, timestamp={}",
                    trip.getId(),
                    filteredDistance.acceptedSegments(),
                    filteredDistance.rejectedSegments(),
                    filteredDistance.jitterSegments(),
                    filteredDistance.speedSegments(),
                    filteredDistance.gapSegments(),
                    filteredDistance.timestampSegments()
            );
        }
        if (filteredDistance.acceptedSegments() == 0) {
            log.warn(
                    "All GPS segments were rejected for actual fare; using estimated distance: "
                            + "tripId={}, historyPoints={}",
                    trip.getId(), history.size()
            );
            return Optional.empty();
        }
        return Optional.of(distanceKm(filteredDistance.totalMeters()));
    }

    private FilteredDistance filteredDistance(List<TripLocationHistory> history) {
        double totalMeters = 0.0;
        int acceptedSegments = 0;
        int jitterSegments = 0;
        int speedSegments = 0;
        int gapSegments = 0;
        int timestampSegments = 0;
        TripLocationHistory previous = history.get(0);

        for (int index = 1; index < history.size(); index++) {
            TripLocationHistory next = history.get(index);
            double elapsedSeconds = elapsedSeconds(previous, next);
            if (elapsedSeconds <= 0) {
                timestampSegments++;
                continue;
            }
            if (elapsedSeconds > filterProperties.getMaxSegmentGapSeconds()) {
                gapSegments++;
                previous = next;
                continue;
            }

            double segmentMeters = haversineMeters(previous.getLocation(), next.getLocation());
            if (segmentMeters < filterProperties.getMinMovementMeters()) {
                jitterSegments++;
                continue;
            }
            if (segmentMeters / elapsedSeconds > filterProperties.getMaxSpeedMetersPerSecond()) {
                speedSegments++;
                continue;
            }

            totalMeters += segmentMeters;
            acceptedSegments++;
            previous = next;
        }

        return new FilteredDistance(
                totalMeters,
                acceptedSegments,
                jitterSegments,
                speedSegments,
                gapSegments,
                timestampSegments
        );
    }

    private double unfilteredDistanceMeters(List<TripLocationHistory> history) {
        double totalMeters = 0.0;
        Point previousPoint = history.get(0).getLocation();
        for (int index = 1; index < history.size(); index++) {
            Point nextPoint = history.get(index).getLocation();
            totalMeters += haversineMeters(previousPoint, nextPoint);
            previousPoint = nextPoint;
        }
        return totalMeters;
    }

    private double elapsedSeconds(TripLocationHistory previous, TripLocationHistory next) {
        if (previous.getRecordedAt() == null || next.getRecordedAt() == null) {
            return -1;
        }
        return Duration.between(previous.getRecordedAt(), next.getRecordedAt()).toNanos()
                / 1_000_000_000.0;
    }

    private BigDecimal distanceKm(double totalMeters) {
        return BigDecimal.valueOf(totalMeters / 1000.0).setScale(2, RoundingMode.HALF_UP);
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

    private record FilteredDistance(
            double totalMeters,
            int acceptedSegments,
            int jitterSegments,
            int speedSegments,
            int gapSegments,
            int timestampSegments
    ) {
        private int rejectedSegments() {
            return jitterSegments + speedSegments + gapSegments + timestampSegments;
        }
    }
}
