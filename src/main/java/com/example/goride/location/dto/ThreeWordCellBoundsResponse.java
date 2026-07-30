package com.example.goride.location.dto;

public record ThreeWordCellBoundsResponse(
        LocationCoordinateResponse southwest,
        LocationCoordinateResponse northeast
) {
}
