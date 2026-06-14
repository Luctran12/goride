package com.example.goride.booking.service.distance;

import java.math.BigDecimal;
import java.util.List;

public record RouteGeometry(
        String type,
        List<List<BigDecimal>> coordinates
) {
}
