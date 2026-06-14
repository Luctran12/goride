package com.example.goride.driver.service;

import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.driver.domain.DriverProfile;
import com.example.goride.driver.dto.DriverHeartbeatRequest;
import com.example.goride.driver.dto.DriverHeartbeatResponse;
import com.example.goride.driver.repository.DriverProfileRepository;
import com.example.goride.driver.service.availability.DriverAvailabilityProperties;
import com.example.goride.driver.service.availability.DriverAvailabilityStore;
import com.example.goride.user.domain.User;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class DriverHeartbeatService {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private final DriverProfileRepository driverProfileRepository;
    private final DriverAvailabilityStore driverAvailabilityStore;
    private final DriverAvailabilityProperties properties;
    private final Clock clock;

    public DriverHeartbeatService(
            DriverProfileRepository driverProfileRepository,
            DriverAvailabilityStore driverAvailabilityStore,
            DriverAvailabilityProperties properties,
            Clock clock
    ) {
        this.driverProfileRepository = driverProfileRepository;
        this.driverAvailabilityStore = driverAvailabilityStore;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public DriverHeartbeatResponse heartbeat(Long userId, DriverHeartbeatRequest request) {
        DriverProfile profile = driverProfileRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DRIVER_PROFILE_NOT_FOUND));
        if (!profile.isOnline()) {
            throw new BusinessException(ErrorCode.DRIVER_NOT_AVAILABLE, "Driver must go online before heartbeating");
        }

        Instant heartbeatAt = clock.instant();
        Point location = GEOMETRY_FACTORY.createPoint(new Coordinate(
                request.lng().doubleValue(),
                request.lat().doubleValue()
        ));
        DriverAvailabilityStore.DriverAvailability availability = availabilityFrom(profile, request);
        if (!driverAvailabilityStore.refreshHeartbeat(availability)) {
            throw new BusinessException(
                    ErrorCode.DRIVER_NOT_AVAILABLE,
                    "Driver heartbeat expired; go online again"
            );
        }

        profile.recordHeartbeat(location, heartbeatAt);
        driverProfileRepository.save(profile);
        return new DriverHeartbeatResponse(
                true,
                heartbeatAt,
                heartbeatAt.plus(properties.heartbeatTimeout())
        );
    }

    private DriverAvailabilityStore.DriverAvailability availabilityFrom(
            DriverProfile profile,
            DriverHeartbeatRequest request
    ) {
        User user = profile.getUser();
        return new DriverAvailabilityStore.DriverAvailability(
                user.getId(),
                request.lat(),
                request.lng(),
                profile.getVehicleType(),
                profile.getAverageRating(),
                user.getFullName(),
                user.getAvatarUrl()
        );
    }
}
