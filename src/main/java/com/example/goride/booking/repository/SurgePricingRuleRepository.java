package com.example.goride.booking.repository;

import com.example.goride.booking.domain.SurgePricingRule;
import com.example.goride.driver.domain.VehicleType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SurgePricingRuleRepository extends JpaRepository<SurgePricingRule, Long> {
    List<SurgePricingRule> findAllByOrderByVehicleTypeAscActiveDescMinDemandSupplyRatioAscMultiplierAsc();

    List<SurgePricingRule> findByVehicleTypeAndActiveTrueOrderByMinDemandSupplyRatioDescMultiplierDesc(
            VehicleType vehicleType
    );
}