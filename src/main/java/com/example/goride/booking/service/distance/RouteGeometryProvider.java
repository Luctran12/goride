package com.example.goride.booking.service.distance;

public interface RouteGeometryProvider {
    RoutePlan route(Location origin, Location destination);
}
