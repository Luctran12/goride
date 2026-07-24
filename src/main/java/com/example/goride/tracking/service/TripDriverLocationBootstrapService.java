package com.example.goride.tracking.service;

import com.example.goride.driver.service.availability.DriverAvailabilityStore;
import com.example.goride.tracking.dto.DriverLocationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TripDriverLocationBootstrapService {
    private static final Logger log = LoggerFactory.getLogger(TripDriverLocationBootstrapService.class);

    private final DriverAvailabilityStore driverAvailabilityStore;
    private final LatestDriverLocationStore latestDriverLocationStore;
    private final TripLocationNotifier tripLocationNotifier;

    public TripDriverLocationBootstrapService(
            DriverAvailabilityStore driverAvailabilityStore,
            LatestDriverLocationStore latestDriverLocationStore,
            TripLocationNotifier tripLocationNotifier
    ) {
        this.driverAvailabilityStore = driverAvailabilityStore;
        this.latestDriverLocationStore = latestDriverLocationStore;
        this.tripLocationNotifier = tripLocationNotifier;
    }

    public void bootstrap(Long tripId, Long driverId) {
        DriverAvailabilityStore.DriverLocation location;
        try {
            location = driverAvailabilityStore.findLocation(driverId).orElse(null);
        } catch (RuntimeException exception) {
            log.warn("Unable to read online driver location for accepted trip tripId={} driverId={}",
                    tripId, driverId, exception);
            return;
        }
        if (location == null) {
            log.debug("No online driver location available for accepted trip tripId={} driverId={}", tripId, driverId);
            return;
        }

        DriverLocationResponse response = new DriverLocationResponse(
                tripId,
                driverId,
                location.latitude(),
                location.longitude(),
                null,
                null,
                location.updatedAt()
        );
        cacheLocation(response);
        broadcastLocation(response);
    }

    private void cacheLocation(DriverLocationResponse response) {
        try {
            latestDriverLocationStore.save(new LatestDriverLocationStore.LatestDriverLocation(
                    response.tripId(),
                    response.driverId(),
                    response.lat(),
                    response.lng(),
                    response.bearing(),
                    response.speed(),
                    response.updatedAt()
            ));
        } catch (RuntimeException exception) {
            log.warn("Unable to cache bootstrapped driver location tripId={} driverId={}",
                    response.tripId(), response.driverId(), exception);
        }
    }

    private void broadcastLocation(DriverLocationResponse response) {
        try {
            tripLocationNotifier.broadcastDriverLocation(response.tripId(), response);
        } catch (RuntimeException exception) {
            log.warn("Unable to broadcast bootstrapped driver location tripId={} driverId={}",
                    response.tripId(), response.driverId(), exception);
        }
    }
}