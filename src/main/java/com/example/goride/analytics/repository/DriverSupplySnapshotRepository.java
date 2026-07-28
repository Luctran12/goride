package com.example.goride.analytics.repository;

import com.example.goride.analytics.domain.DriverSupplySnapshot;
import com.example.goride.driver.domain.VehicleType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface DriverSupplySnapshotRepository extends JpaRepository<DriverSupplySnapshot, Long> {
    Optional<DriverSupplySnapshot> findByBucketStartAndServiceAreaIdAndVehicleType(
            Instant bucketStart,
            Long serviceAreaId,
            VehicleType vehicleType
    );

    Optional<DriverSupplySnapshot> findByBucketStartAndServiceAreaIsNullAndVehicleType(
            Instant bucketStart,
            VehicleType vehicleType
    );
}
