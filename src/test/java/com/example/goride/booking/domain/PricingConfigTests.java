package com.example.goride.booking.domain;

import com.example.goride.driver.domain.VehicleType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PricingConfigTests {
    @Test
    void estimateFareAppliesFormulaAndSurge() {
        PricingConfig pricingConfig = samplePricingConfig(BigDecimal.valueOf(1.2));

        BigDecimal fare = pricingConfig.estimateFare(BigDecimal.valueOf(4.5), 12);

        assertThat(fare).isEqualByComparingTo(BigDecimal.valueOf(37920));
    }

    @Test
    void estimateFareUsesMinimumFareBeforeSurge() {
        PricingConfig pricingConfig = samplePricingConfig(BigDecimal.valueOf(1.5));

        BigDecimal fare = pricingConfig.estimateFare(BigDecimal.valueOf(0.5), 2);

        assertThat(fare).isEqualByComparingTo(BigDecimal.valueOf(22500));
    }

    @Test
    void createRejectsNegativeRate() {
        assertThatThrownBy(() -> PricingConfig.create(
                VehicleType.MOTORBIKE,
                BigDecimal.valueOf(10000),
                BigDecimal.valueOf(-1),
                BigDecimal.ZERO,
                BigDecimal.valueOf(15000),
                BigDecimal.ONE,
                Instant.now()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("perKmRate must not be negative");
    }

    private PricingConfig samplePricingConfig(BigDecimal surgeMultiplier) {
        return PricingConfig.create(
                VehicleType.MOTORBIKE,
                BigDecimal.valueOf(10000),
                BigDecimal.valueOf(4000),
                BigDecimal.valueOf(300),
                BigDecimal.valueOf(15000),
                surgeMultiplier,
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }
}
