package com.example.goride.booking.service.distance;

import java.util.Optional;

public interface RouteEstimateCache {
    Optional<DistanceEstimate> find(String routingProfile, Location pickup, Location dropoff);

    void put(
            String routingProfile,
            Location pickup,
            Location dropoff,
            DistanceEstimate estimate
    );
}
