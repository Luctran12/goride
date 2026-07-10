package com.example.goride.servicearea.dto;

import com.example.goride.servicearea.domain.ServiceArea;
import org.locationtech.jts.geom.Coordinate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public record ServiceAreaResponse(
        Long id,
        String name,
        String cityName,
        String countryCode,
        boolean active,
        List<ServiceAreaCoordinateResponse> boundary,
        Instant createdAt,
        Instant updatedAt
) {
    public static ServiceAreaResponse from(ServiceArea area) {
        return new ServiceAreaResponse(
                area.getId(),
                area.getName(),
                area.getCityName(),
                area.getCountryCode(),
                area.isActive(),
                boundaryFrom(area),
                area.getCreatedAt(),
                area.getUpdatedAt()
        );
    }

    private static List<ServiceAreaCoordinateResponse> boundaryFrom(ServiceArea area) {
        Coordinate[] coordinates = area.getBoundary().getExteriorRing().getCoordinates();
        List<ServiceAreaCoordinateResponse> response = new ArrayList<>();
        for (int index = 0; index < coordinates.length - 1; index++) {
            Coordinate coordinate = coordinates[index];
            response.add(new ServiceAreaCoordinateResponse(
                    BigDecimal.valueOf(coordinate.getY()),
                    BigDecimal.valueOf(coordinate.getX())
            ));
        }
        return response;
    }
}