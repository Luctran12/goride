package com.example.goride.location.dto;

import java.math.BigDecimal;

public record LocationCoordinateResponse(
        BigDecimal lat,
        BigDecimal lng
) {
}
