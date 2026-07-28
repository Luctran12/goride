package com.example.goride.analytics.domain;

import com.example.goride.driver.domain.VehicleType;
import com.example.goride.servicearea.domain.ServiceArea;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Check;

import java.time.Instant;

@Entity
@Table(
        name = "driver_supply_snapshots",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_driver_supply_snapshots_bucket_area_vehicle",
                        columnNames = {"bucket_start", "service_area_id", "vehicle_type"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_driver_supply_snapshots_bucket_vehicle",
                        columnList = "bucket_start, vehicle_type"
                ),
                @Index(
                        name = "idx_driver_supply_snapshots_area_bucket",
                        columnList = "service_area_id, bucket_start"
                )
        }
)
@Check(
        name = "chk_driver_supply_snapshots_entity",
        constraints = """
                online_drivers >= 0
                AND available_drivers >= 0
                AND busy_drivers >= 0
                AND available_drivers + busy_drivers <= online_drivers
                AND sampled_at >= bucket_start
                """
)
public class DriverSupplySnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bucket_start", nullable = false, updatable = false)
    private Instant bucketStart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_area_id", updatable = false)
    private ServiceArea serviceArea;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 20, updatable = false)
    private VehicleType vehicleType;

    @Column(name = "online_drivers", nullable = false)
    private int onlineDrivers;

    @Column(name = "available_drivers", nullable = false)
    private int availableDrivers;

    @Column(name = "busy_drivers", nullable = false)
    private int busyDrivers;

    @Column(name = "sampled_at", nullable = false)
    private Instant sampledAt;

    protected DriverSupplySnapshot() {
    }

    public static DriverSupplySnapshot record(
            Instant bucketStart,
            ServiceArea serviceArea,
            VehicleType vehicleType,
            int onlineDrivers,
            int availableDrivers,
            int busyDrivers,
            Instant sampledAt
    ) {
        validateCounts(onlineDrivers, availableDrivers, busyDrivers);
        Instant normalizedBucketStart = requireNonNull(bucketStart, "bucketStart");
        Instant normalizedSampledAt = requireNonNull(sampledAt, "sampledAt");
        if (normalizedSampledAt.isBefore(normalizedBucketStart)) {
            throw new IllegalArgumentException("sampledAt must not be before bucketStart");
        }

        DriverSupplySnapshot snapshot = new DriverSupplySnapshot();
        snapshot.bucketStart = normalizedBucketStart;
        snapshot.serviceArea = serviceArea;
        snapshot.vehicleType = requireNonNull(vehicleType, "vehicleType");
        snapshot.onlineDrivers = onlineDrivers;
        snapshot.availableDrivers = availableDrivers;
        snapshot.busyDrivers = busyDrivers;
        snapshot.sampledAt = normalizedSampledAt;
        return snapshot;
    }

    @PrePersist
    void prePersist() {
        requireNonNull(bucketStart, "bucketStart");
        requireNonNull(vehicleType, "vehicleType");
        requireNonNull(sampledAt, "sampledAt");
        validateCounts(onlineDrivers, availableDrivers, busyDrivers);
        if (sampledAt.isBefore(bucketStart)) {
            throw new IllegalStateException("sampledAt must not be before bucketStart");
        }
    }

    public Long getId() {
        return id;
    }

    public Instant getBucketStart() {
        return bucketStart;
    }

    public ServiceArea getServiceArea() {
        return serviceArea;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public int getOnlineDrivers() {
        return onlineDrivers;
    }

    public int getAvailableDrivers() {
        return availableDrivers;
    }

    public int getBusyDrivers() {
        return busyDrivers;
    }

    public Instant getSampledAt() {
        return sampledAt;
    }

    private static void validateCounts(int onlineDrivers, int availableDrivers, int busyDrivers) {
        if (onlineDrivers < 0 || availableDrivers < 0 || busyDrivers < 0) {
            throw new IllegalArgumentException("Driver supply counts must not be negative");
        }
        if ((long) availableDrivers + busyDrivers > onlineDrivers) {
            throw new IllegalArgumentException(
                    "availableDrivers plus busyDrivers must not exceed onlineDrivers"
            );
        }
    }

    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }
}
