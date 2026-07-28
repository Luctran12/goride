package com.example.goride.analytics.service;

import com.example.goride.analytics.config.AnalyticsTelemetryProperties;
import com.example.goride.analytics.repository.DriverSupplySnapshotRepository;
import com.example.goride.analytics.service.DriverSupplySource.DriverSupplyObservation;
import com.example.goride.analytics.service.DriverSupplySource.DriverSupplyReadResult;
import com.example.goride.analytics.service.DriverSupplySource.DriverSupplyStatus;
import com.example.goride.driver.domain.VehicleType;
import com.example.goride.servicearea.domain.ServiceArea;
import com.example.goride.servicearea.repository.ServiceAreaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DriverSupplySnapshotService {
    private static final Logger log = LoggerFactory.getLogger(DriverSupplySnapshotService.class);

    private final DriverSupplySource supplySource;
    private final ServiceAreaRepository serviceAreaRepository;
    private final DriverSupplySnapshotRepository snapshotRepository;
    private final AnalyticsTelemetryProperties properties;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public DriverSupplySnapshotService(
            DriverSupplySource supplySource,
            ServiceAreaRepository serviceAreaRepository,
            DriverSupplySnapshotRepository snapshotRepository,
            AnalyticsTelemetryProperties properties,
            MeterRegistry meterRegistry,
            Clock clock
    ) {
        this.supplySource = supplySource;
        this.serviceAreaRepository = serviceAreaRepository;
        this.snapshotRepository = snapshotRepository;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    @Transactional
    public int captureCurrentSupply() {
        DriverSupplyReadResult readResult = supplySource.readCurrentSupply();
        if (!readResult.complete()) {
            meterRegistry.counter(
                    "goride.analytics.supply.snapshot.skipped",
                    "reason",
                    "malformed_active_driver"
            ).increment();
            log.warn(
                    "Skipping driver-supply snapshot because active Redis entries are incomplete malformed={} stale={}",
                    readResult.malformedActiveDrivers(),
                    readResult.skippedStaleDrivers()
            );
            return 0;
        }

        Instant sampledAt = clock.instant();
        Instant bucketStart = bucketStart(sampledAt);
        List<ServiceArea> serviceAreas = serviceAreaRepository.findByActiveTrueOrderByCityNameAscNameAsc();
        Map<SupplyKey, SupplyCounts> counts = initializeCounts(serviceAreas);
        readResult.observations().forEach(observation ->
                accumulate(counts, serviceAreas, observation)
        );

        counts.forEach((key, value) -> snapshotRepository.upsertSnapshot(
                bucketStart,
                key.serviceArea() == null ? null : key.serviceArea().getId(),
                key.vehicleType().name(),
                value.online,
                value.available,
                value.busy,
                sampledAt
        ));
        meterRegistry.counter("goride.analytics.supply.snapshot.captured").increment();
        log.info(
                "Captured driver-supply snapshot bucketStart={} rows={} observations={} staleSkipped={}",
                bucketStart,
                counts.size(),
                readResult.observations().size(),
                readResult.skippedStaleDrivers()
        );
        return counts.size();
    }

    private Map<SupplyKey, SupplyCounts> initializeCounts(List<ServiceArea> serviceAreas) {
        Map<SupplyKey, SupplyCounts> counts = new LinkedHashMap<>();
        for (VehicleType vehicleType : VehicleType.values()) {
            counts.put(new SupplyKey(null, vehicleType), new SupplyCounts());
            for (ServiceArea serviceArea : serviceAreas) {
                counts.put(new SupplyKey(serviceArea, vehicleType), new SupplyCounts());
            }
        }
        return counts;
    }

    private void accumulate(
            Map<SupplyKey, SupplyCounts> counts,
            List<ServiceArea> serviceAreas,
            DriverSupplyObservation observation
    ) {
        counts.get(new SupplyKey(null, observation.vehicleType())).add(observation.status());
        serviceAreas.stream()
                .filter(area -> area.covers(observation.location()))
                .findFirst()
                .ifPresent(area ->
                        counts.get(new SupplyKey(area, observation.vehicleType()))
                                .add(observation.status())
                );
    }

    private Instant bucketStart(Instant sampledAt) {
        long interval = properties.getSupplySnapshotIntervalSeconds();
        long epochSecond = sampledAt.getEpochSecond();
        return Instant.ofEpochSecond(epochSecond - Math.floorMod(epochSecond, interval));
    }

    private record SupplyKey(ServiceArea serviceArea, VehicleType vehicleType) {
    }

    private static final class SupplyCounts {
        private int online;
        private int available;
        private int busy;

        private void add(DriverSupplyStatus status) {
            online++;
            if (status == DriverSupplyStatus.AVAILABLE) {
                available++;
            } else {
                busy++;
            }
        }
    }
}
