package com.example.goride.booking.config;

import com.example.goride.booking.domain.PricingConfig;
import com.example.goride.booking.repository.PricingConfigRepository;
import com.example.goride.driver.domain.VehicleType;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

@Component
public class PricingConfigSeeder implements ApplicationRunner {
    private static final Instant DEFAULT_EFFECTIVE_FROM = Instant.parse("2026-01-01T00:00:00Z");

    private final PricingConfigRepository pricingConfigRepository;

    public PricingConfigSeeder(PricingConfigRepository pricingConfigRepository) {
        this.pricingConfigRepository = pricingConfigRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedIfMissing(
                VehicleType.MOTORBIKE,
                BigDecimal.valueOf(10000),
                BigDecimal.valueOf(4000),
                BigDecimal.valueOf(300),
                BigDecimal.valueOf(15000)
        );
        seedIfMissing(
                VehicleType.CAR_4_SEAT,
                BigDecimal.valueOf(15000),
                BigDecimal.valueOf(8000),
                BigDecimal.valueOf(500),
                BigDecimal.valueOf(25000)
        );
        seedIfMissing(
                VehicleType.CAR_7_SEAT,
                BigDecimal.valueOf(20000),
                BigDecimal.valueOf(9000),
                BigDecimal.valueOf(600),
                BigDecimal.valueOf(30000)
        );
    }

    private void seedIfMissing(
            VehicleType vehicleType,
            BigDecimal baseFare,
            BigDecimal perKmRate,
            BigDecimal perMinuteRate,
            BigDecimal minimumFare
    ) {
        if (pricingConfigRepository.existsByVehicleTypeAndActiveTrue(vehicleType)) {
            return;
        }

        pricingConfigRepository.save(PricingConfig.create(
                vehicleType,
                baseFare,
                perKmRate,
                perMinuteRate,
                minimumFare,
                BigDecimal.ONE,
                DEFAULT_EFFECTIVE_FROM
        ));
    }
}
