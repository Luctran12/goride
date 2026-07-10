package com.example.goride.servicearea.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ServiceAreaCreateRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 120) String cityName,
        @Size(min = 2, max = 2) String countryCode,
        Boolean active,
        @NotEmpty @Size(min = 3, max = 200) List<@Valid ServiceAreaCoordinateRequest> boundary
) {
}