package com.example.goride.booking.service.distance;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class MockDistanceService implements DistanceService {
    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double ROAD_FACTOR = 1.25;
    private static final double AVERAGE_CITY_SPEED_KMH = 25.0;

    @Override
    public DistanceEstimate estimate(Location pickup, Location dropoff) {
        double straightLineKm = haversineKm(
                pickup.latitude().doubleValue(),
                pickup.longitude().doubleValue(),
                dropoff.latitude().doubleValue(),
                dropoff.longitude().doubleValue()
        );
        double roadDistanceKm = Math.max(0.1, straightLineKm * ROAD_FACTOR);
        int durationMinutes = Math.max(1, (int) Math.ceil(roadDistanceKm / AVERAGE_CITY_SPEED_KMH * 60));
        return new DistanceEstimate(
                BigDecimal.valueOf(roadDistanceKm).setScale(2, RoundingMode.HALF_UP),
                durationMinutes
        );
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}
