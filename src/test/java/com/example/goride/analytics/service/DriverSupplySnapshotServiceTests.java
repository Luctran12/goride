package com.example.goride.analytics.service;

import com.example.goride.analytics.config.AnalyticsTelemetryProperties;
import com.example.goride.analytics.repository.DriverSupplySnapshotRepository;
import com.example.goride.analytics.service.DriverSupplySource.DriverSupplyObservation;
import com.example.goride.analytics.service.DriverSupplySource.DriverSupplyReadResult;
import com.example.goride.analytics.service.DriverSupplySource.DriverSupplyStatus;
import com.example.goride.driver.domain.VehicleType;
import com.example.goride.servicearea.domain.ServiceArea;
import com.example.goride.servicearea.repository.ServiceAreaRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverSupplySnapshotServiceTests {
    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);
    private static final Instant SAMPLED_AT = Instant.parse("2026-07-28T07:07:30Z");
    private static final Instant BUCKET_START = Instant.parse("2026-07-28T07:05:00Z");

    @Mock
    private DriverSupplySource supplySource;

    @Mock
    private ServiceAreaRepository serviceAreaRepository;

    @Mock
    private DriverSupplySnapshotRepository snapshotRepository;

    private SimpleMeterRegistry meterRegistry;
    private DriverSupplySnapshotService snapshotService;

    @BeforeEach
    void setUp() {
        AnalyticsTelemetryProperties properties = new AnalyticsTelemetryProperties();
        properties.setSupplySnapshotIntervalSeconds(300);
        meterRegistry = new SimpleMeterRegistry();
        snapshotService = new DriverSupplySnapshotService(
                supplySource,
                serviceAreaRepository,
                snapshotRepository,
                properties,
                meterRegistry,
                Clock.fixed(SAMPLED_AT, ZoneOffset.UTC)
        );
    }

    @Test
    void capturesGlobalAndServiceAreaCountsIncludingExplicitZeroRows() {
        ServiceArea serviceArea = serviceArea();
        when(serviceAreaRepository.findByActiveTrueOrderByCityNameAscNameAsc())
                .thenReturn(List.of(serviceArea));
        when(supplySource.readCurrentSupply()).thenReturn(new DriverSupplyReadResult(
                true,
                List.of(
                        observation(10L, VehicleType.MOTORBIKE, DriverSupplyStatus.AVAILABLE, 106.70, 10.77),
                        observation(11L, VehicleType.MOTORBIKE, DriverSupplyStatus.BUSY, 106.90, 10.90),
                        observation(12L, VehicleType.CAR_4_SEAT, DriverSupplyStatus.BUSY, 106.71, 10.78)
                ),
                1,
                0
        ));

        int rows = snapshotService.captureCurrentSupply();

        assertThat(rows).isEqualTo(6);
        verify(snapshotRepository).upsertSnapshot(
                BUCKET_START, null, "MOTORBIKE", 2, 1, 1, SAMPLED_AT
        );
        verify(snapshotRepository).upsertSnapshot(
                BUCKET_START, 44L, "MOTORBIKE", 1, 1, 0, SAMPLED_AT
        );
        verify(snapshotRepository).upsertSnapshot(
                BUCKET_START, null, "CAR_4_SEAT", 1, 0, 1, SAMPLED_AT
        );
        verify(snapshotRepository).upsertSnapshot(
                BUCKET_START, 44L, "CAR_4_SEAT", 1, 0, 1, SAMPLED_AT
        );
        verify(snapshotRepository).upsertSnapshot(
                BUCKET_START, null, "CAR_7_SEAT", 0, 0, 0, SAMPLED_AT
        );
        verify(snapshotRepository).upsertSnapshot(
                BUCKET_START, 44L, "CAR_7_SEAT", 0, 0, 0, SAMPLED_AT
        );
        assertThat(meterRegistry.counter("goride.analytics.supply.snapshot.captured").count())
                .isEqualTo(1.0);
    }

    @Test
    void skipsWholeBucketWhenAnActiveRedisEntryIsMalformed() {
        when(supplySource.readCurrentSupply()).thenReturn(new DriverSupplyReadResult(
                false,
                List.of(),
                0,
                1
        ));

        int rows = snapshotService.captureCurrentSupply();

        assertThat(rows).isZero();
        verifyNoInteractions(serviceAreaRepository, snapshotRepository);
        assertThat(meterRegistry.find("goride.analytics.supply.snapshot.skipped")
                .tag("reason", "malformed_active_driver")
                .counter()
                .count()).isEqualTo(1.0);
    }

    private ServiceArea serviceArea() {
        ServiceArea area = ServiceArea.create(
                "Central",
                "Ho Chi Minh City",
                "VN",
                GEOMETRY_FACTORY.createPolygon(new Coordinate[]{
                        new Coordinate(106.60, 10.70),
                        new Coordinate(106.80, 10.70),
                        new Coordinate(106.80, 10.85),
                        new Coordinate(106.60, 10.85),
                        new Coordinate(106.60, 10.70)
                }),
                true
        );
        ReflectionTestUtils.setField(area, "id", 44L);
        return area;
    }

    private DriverSupplyObservation observation(
            Long driverId,
            VehicleType vehicleType,
            DriverSupplyStatus status,
            double longitude,
            double latitude
    ) {
        Point location = GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
        return new DriverSupplyObservation(driverId, vehicleType, status, location);
    }
}
