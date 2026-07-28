package com.example.goride.analytics.service;

import com.example.goride.driver.domain.VehicleType;
import org.locationtech.jts.geom.Point;

import java.util.List;

public interface DriverSupplySource {
    DriverSupplyReadResult readCurrentSupply();

    record DriverSupplyReadResult(
            boolean complete,
            List<DriverSupplyObservation> observations,
            int skippedStaleDrivers,
            int malformedActiveDrivers
    ) {
        public DriverSupplyReadResult {
            observations = observations == null ? List.of() : List.copyOf(observations);
        }
    }

    record DriverSupplyObservation(
            Long driverId,
            VehicleType vehicleType,
            DriverSupplyStatus status,
            Point location
    ) {
    }

    enum DriverSupplyStatus {
        AVAILABLE,
        BUSY
    }
}
