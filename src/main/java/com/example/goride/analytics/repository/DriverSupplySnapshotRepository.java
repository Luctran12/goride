package com.example.goride.analytics.repository;

import com.example.goride.analytics.domain.DriverSupplySnapshot;
import com.example.goride.driver.domain.VehicleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Modifying
    @Query(value = """
            INSERT INTO driver_supply_snapshots (
                bucket_start,
                service_area_id,
                vehicle_type,
                online_drivers,
                available_drivers,
                busy_drivers,
                sampled_at
            )
            VALUES (
                :bucketStart,
                :serviceAreaId,
                :vehicleType,
                :onlineDrivers,
                :availableDrivers,
                :busyDrivers,
                :sampledAt
            )
            ON CONFLICT (bucket_start, service_area_id, vehicle_type)
            DO UPDATE SET
                online_drivers = EXCLUDED.online_drivers,
                available_drivers = EXCLUDED.available_drivers,
                busy_drivers = EXCLUDED.busy_drivers,
                sampled_at = EXCLUDED.sampled_at
            """, nativeQuery = true)
    int upsertSnapshot(
            @Param("bucketStart") Instant bucketStart,
            @Param("serviceAreaId") Long serviceAreaId,
            @Param("vehicleType") String vehicleType,
            @Param("onlineDrivers") int onlineDrivers,
            @Param("availableDrivers") int availableDrivers,
            @Param("busyDrivers") int busyDrivers,
            @Param("sampledAt") Instant sampledAt
    );
}
