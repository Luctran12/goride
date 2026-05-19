package com.example.goride.matching.service;

import com.example.goride.driver.domain.VehicleType;
import com.example.goride.matching.domain.DriverCandidate;
import com.example.goride.matching.domain.MatchingRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NearestDriverStrategyTests {
    private final NearestDriverStrategy strategy = new NearestDriverStrategy();

    @Test
    void ranksByDistanceAndLimitsResult() {
        MatchingRequest request = request(2);
        List<DriverCandidate> ranked = strategy.rank(request, List.of(
                candidate(10L, 800),
                candidate(11L, 200),
                candidate(12L, 500)
        ));

        assertThat(ranked).extracting(DriverCandidate::driverId).containsExactly(11L, 12L);
    }

    @Test
    void usesDriverIdAsStableTieBreaker() {
        MatchingRequest request = request(3);
        List<DriverCandidate> ranked = strategy.rank(request, List.of(
                candidate(12L, 500),
                candidate(10L, 500),
                candidate(11L, 500)
        ));

        assertThat(ranked).extracting(DriverCandidate::driverId).containsExactly(10L, 11L, 12L);
    }

    private MatchingRequest request(int limit) {
        return new MatchingRequest(
                99L,
                VehicleType.MOTORBIKE,
                BigDecimal.valueOf(10.7769),
                BigDecimal.valueOf(106.7009),
                BigDecimal.valueOf(5),
                limit
        );
    }

    private DriverCandidate candidate(Long driverId, long distanceMeters) {
        return new DriverCandidate(
                driverId,
                distanceMeters,
                VehicleType.MOTORBIKE,
                BigDecimal.valueOf(5.0),
                "Driver " + driverId,
                null
        );
    }
}
