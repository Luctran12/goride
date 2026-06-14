package com.example.goride.driver.service;

import com.example.goride.driver.domain.DriverProfile;
import com.example.goride.driver.repository.DriverProfileRepository;
import com.example.goride.driver.service.availability.DriverAvailabilityProperties;
import com.example.goride.driver.service.availability.DriverAvailabilityStore;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class DriverHeartbeatTimeoutService {
    private final DriverProfileRepository driverProfileRepository;
    private final DriverAvailabilityStore driverAvailabilityStore;
    private final DriverAvailabilityProperties properties;
    private final Clock clock;

    public DriverHeartbeatTimeoutService(
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
    public int expireStaleDrivers() {
        Instant cutoff = clock.instant().minus(properties.heartbeatTimeout());
        List<DriverProfile> staleProfiles = driverProfileRepository.findStaleOnlineProfilesForUpdate(
                cutoff,
                PageRequest.of(0, properties.getCleanupBatchSize())
        );
        staleProfiles.forEach(profile -> profile.goOffline(null));
        driverProfileRepository.saveAll(staleProfiles);

        List<Long> driverIds = staleProfiles.stream()
                .map(profile -> profile.getUser().getId())
                .toList();
        runAfterCommit(() -> driverIds.forEach(driverAvailabilityStore::markOffline));
        return staleProfiles.size();
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
