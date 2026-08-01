package com.example.goride.analytics.service;

import com.example.goride.analytics.service.DriverSupplySource.DriverSupplyObservation;
import com.example.goride.analytics.service.DriverSupplySource.DriverSupplyReadResult;
import com.example.goride.analytics.service.DriverSupplySource.DriverSupplyStatus;
import com.example.goride.driver.domain.VehicleType;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class RedisDriverSupplySource implements DriverSupplySource {
    private static final String ONLINE_DRIVERS_KEY = "drivers:online";
    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    private final StringRedisTemplate redisTemplate;

    public RedisDriverSupplySource(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public DriverSupplyReadResult readCurrentSupply() {
        Set<String> driverIds = redisTemplate.opsForZSet().range(ONLINE_DRIVERS_KEY, 0, -1);
        if (driverIds == null || driverIds.isEmpty()) {
            return new DriverSupplyReadResult(true, List.of(), 0, 0);
        }

        List<DriverSupplyObservation> observations = new ArrayList<>();
        int staleDrivers = 0;
        int malformedActiveDrivers = 0;
        for (String driverIdValue : driverIds) {
            String statusValue = redisTemplate.opsForValue().get(statusKey(driverIdValue));
            if (statusValue == null) {
                staleDrivers++;
                continue;
            }
            Optional<DriverSupplyStatus> status = parseStatus(statusValue);
            if (status.isEmpty()) {
                malformedActiveDrivers++;
                continue;
            }
            Optional<DriverSupplyObservation> observation = toObservation(
                    driverIdValue,
                    status.get()
            );
            if (observation.isEmpty()) {
                malformedActiveDrivers++;
                continue;
            }
            observations.add(observation.get());
        }
        return new DriverSupplyReadResult(
                malformedActiveDrivers == 0,
                observations,
                staleDrivers,
                malformedActiveDrivers
        );
    }

    private Optional<DriverSupplyObservation> toObservation(
            String driverIdValue,
            DriverSupplyStatus status
    ) {
        try {
            Map<Object, Object> metadata = redisTemplate.opsForHash().entries(metaKey(driverIdValue));
            VehicleType vehicleType = VehicleType.valueOf(String.valueOf(metadata.get("vehicleType")));
            List<Point> positions = redisTemplate.opsForGeo().position(ONLINE_DRIVERS_KEY, driverIdValue);
            if (positions == null || positions.isEmpty() || positions.get(0) == null) {
                return Optional.empty();
            }
            Point redisPoint = positions.get(0);
            org.locationtech.jts.geom.Point location = GEOMETRY_FACTORY.createPoint(
                    new Coordinate(redisPoint.getX(), redisPoint.getY())
            );
            return Optional.of(new DriverSupplyObservation(
                    Long.parseLong(driverIdValue),
                    vehicleType,
                    status,
                    location
            ));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private Optional<DriverSupplyStatus> parseStatus(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(DriverSupplyStatus.valueOf(value));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private String statusKey(String driverId) {
        return "driver:" + driverId + ":status";
    }

    private String metaKey(String driverId) {
        return "driver:" + driverId + ":meta";
    }
}
