package com.example.goride.booking.service;

import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.domain.TripStatus;
import com.example.goride.booking.domain.TripStatusHistory;
import com.example.goride.booking.event.BookingCreatedEvent;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.booking.repository.TripStatusHistoryRepository;
import com.example.goride.notification.dto.TripStatusNotification;
import com.example.goride.notification.service.TripRealtimeNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class ScheduledRideDispatchService {
    private static final Logger log = LoggerFactory.getLogger(ScheduledRideDispatchService.class);

    private final TripRepository tripRepository;
    private final TripStatusHistoryRepository tripStatusHistoryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TripRealtimeNotifier tripRealtimeNotifier;
    private final ScheduledRideProperties scheduledRideProperties;
    private final Clock clock;

    public ScheduledRideDispatchService(
            TripRepository tripRepository,
            TripStatusHistoryRepository tripStatusHistoryRepository,
            ApplicationEventPublisher eventPublisher,
            TripRealtimeNotifier tripRealtimeNotifier,
            ScheduledRideProperties scheduledRideProperties,
            Clock clock
    ) {
        this.tripRepository = tripRepository;
        this.tripStatusHistoryRepository = tripStatusHistoryRepository;
        this.eventPublisher = eventPublisher;
        this.tripRealtimeNotifier = tripRealtimeNotifier;
        this.scheduledRideProperties = scheduledRideProperties;
        this.clock = clock;
    }

    @Transactional
    public int dispatchDueScheduledTrips() {
        if (!scheduledRideProperties.isEnabled()) {
            return 0;
        }

        Instant dispatchBefore = Instant.now(clock).plus(scheduledRideProperties.dispatchLeadTime());
        List<Trip> trips = tripRepository.findReadyScheduledTripsForUpdate(
                TripStatus.SCHEDULED,
                dispatchBefore,
                PageRequest.of(0, scheduledRideProperties.getDispatchBatchSize())
        );
        trips.forEach(this::dispatchScheduledTrip);
        return trips.size();
    }

    private void dispatchScheduledTrip(Trip trip) {
        Long tripId = trip.getId();
        Instant scheduledPickupTime = trip.getScheduledPickupTime();
        TripStatus previousStatus = trip.getStatus();
        trip.dispatchScheduled();
        Trip savedTrip = tripRepository.save(trip);
        tripStatusHistoryRepository.save(TripStatusHistory.record(
                savedTrip,
                previousStatus,
                TripStatus.SEARCHING,
                null,
                "Scheduled booking opened for matching"
        ));
        log.info(
                "Scheduled booking opened for matching tripId={} scheduledPickupTime={}",
                tripId,
                scheduledPickupTime
        );
        runAfterCommit(() -> {
            eventPublisher.publishEvent(BookingCreatedEvent.from(savedTrip));
            tripRealtimeNotifier.broadcastTripStatus(tripId, TripStatusNotification.from(savedTrip));
        });
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