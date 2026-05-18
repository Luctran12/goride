package com.example.goride.booking.dto;

import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;

public record BookingLocationResponse(
        BigDecimal lat,
        BigDecimal lng,
        String address
) {
    public static BookingLocationResponse of(Point point, String address) {
        return new BookingLocationResponse(
                BigDecimal.valueOf(point.getY()),
                BigDecimal.valueOf(point.getX()),
                address
        );
    }
}
