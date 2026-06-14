package com.example.goride.driver.dto;

import com.example.goride.booking.service.distance.RouteGeometry;

import java.math.BigDecimal;
import java.util.List;

public record DriverRouteGeometryResponse(
        String type,
        List<List<BigDecimal>> coordinates
) {
    public static DriverRouteGeometryResponse from(RouteGeometry geometry) {
        return new DriverRouteGeometryResponse(geometry.type(), geometry.coordinates());
    }
}
