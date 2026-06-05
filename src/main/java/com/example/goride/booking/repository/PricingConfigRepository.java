package com.example.goride.booking.repository;

import com.example.goride.booking.domain.PricingConfig;
import com.example.goride.driver.domain.VehicleType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PricingConfigRepository extends JpaRepository<PricingConfig, Long> {
    boolean existsByVehicleTypeAndActiveTrue(VehicleType vehicleType);

    List<PricingConfig> findByActiveTrueOrderByVehicleTypeAscEffectiveFromDesc();

    List<PricingConfig> findAllByOrderByVehicleTypeAscEffectiveFromDesc();

    List<PricingConfig> findByVehicleTypeAndActiveTrue(VehicleType vehicleType);

    Optional<PricingConfig> findFirstByVehicleTypeAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            VehicleType vehicleType,
            Instant effectiveAt
    );
}
