package com.example.goride.booking.service.distance;

public interface DistanceService {
    DistanceEstimate estimate(Location pickup, Location dropoff);
}
