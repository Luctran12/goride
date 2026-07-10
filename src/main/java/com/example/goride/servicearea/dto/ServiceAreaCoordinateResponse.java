package com.example.goride.servicearea.dto;

import java.math.BigDecimal;

public record ServiceAreaCoordinateResponse(
        BigDecimal lat,
        BigDecimal lng
) {
}