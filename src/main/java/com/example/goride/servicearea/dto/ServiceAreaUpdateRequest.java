package com.example.goride.servicearea.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ServiceAreaUpdateRequest(
        @Size(max = 120) String name,
        @Size(max = 120) String cityName,
        @Size(min = 2, max = 2) String countryCode,
        Boolean active,
        @Size(min = 3, max = 200) List<@Valid ServiceAreaCoordinateRequest> boundary
) {
}