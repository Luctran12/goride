package com.example.goride.booking.service.distance;

import java.util.Optional;

public interface ProviderRouteEstimateService {
    Optional<DistanceEstimate> estimateProviderRoute(Location origin, Location destination);
}