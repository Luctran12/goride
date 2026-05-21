package com.example.goride.payment.service;

import java.math.BigDecimal;

public record TripCompletionFare(
        BigDecimal finalFare,
        BigDecimal actualDistanceKm,
        int actualDurationMin
) {
}
