package com.example.goride.tracking.service;

import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.domain.TripStatus;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.tracking.domain.TripLocationHistory;
import com.example.goride.tracking.dto.DriverLocationResponse;
import com.example.goride.tracking.dto.DriverLocationUpdateRequest;
import com.example.goride.tracking.repository.TripLocationHistoryRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.Objects;

@Service
public class TripLocationTrackingService {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final Set<TripStatus> DRIVER_TRACKING_STATUSES = Set.of(
            TripStatus.ACCEPTED,
            TripStatus.ARRIVED,
            TripStatus.IN_PROGRESS
    );

    private final TripRepository tripRepository;
    private final TripLocationHistoryRepository tripLocationHistoryRepository;
    private final LatestDriverLocationStore latestDriverLocationStore;
    private final TripLocationNotifier tripLocationNotifier;

    public TripLocationTrackingService(
            TripRepository tripRepository,
            TripLocationHistoryRepository tripLocationHistoryRepository,
            LatestDriverLocationStore latestDriverLocationStore,
            TripLocationNotifier tripLocationNotifier
    ) {
        this.tripRepository = tripRepository;
        this.tripLocationHistoryRepository = tripLocationHistoryRepository;
        this.latestDriverLocationStore = latestDriverLocationStore;
        this.tripLocationNotifier = tripLocationNotifier;
    }

    @Transactional
    public DriverLocationResponse updateDriverLocation(Long driverId, DriverLocationUpdateRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Driver location is required");
        }

        Trip trip = tripRepository.findFirstByDriverIdAndStatusInAndDeletedAtIsNullOrderByAcceptedAtDesc(
                        driverId,
                        DRIVER_TRACKING_STATUSES
                )
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TRIP_NOT_FOUND,
                        "Driver has no active assigned trip"
                ));

        Point location = toPoint(request.lat(), request.lng());
        Long tripId = trip.getId();
        DriverLocationResponse response = recordHistoryWhenTripStarted(trip, driverId, request, location);
        LatestDriverLocationStore.LatestDriverLocation latestLocation = latestFrom(response);
        runAfterCommit(() -> {
            latestDriverLocationStore.save(latestLocation);
            tripLocationNotifier.broadcastDriverLocation(tripId, response);
        });
        return response;
    }

    private DriverLocationResponse recordHistoryWhenTripStarted(
            Trip trip,
            Long driverId,
            DriverLocationUpdateRequest request,
            Point location
    ) {
        if (trip.getStatus() != TripStatus.IN_PROGRESS) {
            return responseFrom(trip, driverId, location, request, Instant.now());
        }

        TripLocationHistory history = tripLocationHistoryRepository.save(TripLocationHistory.record(
                trip,
                location,
                request.bearing(),
                request.speed()
        ));
        return responseFrom(trip, driverId, history);
    }

    @Transactional(readOnly = true)
    public DriverLocationResponse getLatestDriverLocation(Long passengerId, Long tripId) {
        Trip trip = tripRepository.findByIdAndDeletedAtIsNull(tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
        if (!Objects.equals(trip.getPassenger().getId(), passengerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only the passenger of this trip can view driver location");
        }
        if (trip.getDriver() == null) {
            throw new BusinessException(ErrorCode.DRIVER_LOCATION_NOT_FOUND);
        }

        LatestDriverLocationStore.LatestDriverLocation location = latestDriverLocationStore
                .findByDriverId(trip.getDriver().getId())
                .filter(latestLocation -> Objects.equals(latestLocation.tripId(), tripId))
                .orElseThrow(() -> new BusinessException(ErrorCode.DRIVER_LOCATION_NOT_FOUND));
        return new DriverLocationResponse(
                location.tripId(),
                location.driverId(),
                location.lat(),
                location.lng(),
                location.bearing(),
                location.speed(),
                location.updatedAt()
        );
    }

    private DriverLocationResponse responseFrom(Trip trip, Long driverId, TripLocationHistory history) {
        Point location = history.getLocation();
        return responseFrom(
                trip,
                driverId,
                location,
                new DriverLocationUpdateRequest(
                        BigDecimal.valueOf(location.getY()),
                        BigDecimal.valueOf(location.getX()),
                        history.getBearing(),
                        history.getSpeed()
                ),
                history.getRecordedAt()
        );
    }

    private DriverLocationResponse responseFrom(
            Trip trip,
            Long driverId,
            Point location,
            DriverLocationUpdateRequest request,
            Instant updatedAt
    ) {
        return new DriverLocationResponse(
                trip.getId(),
                driverId,
                BigDecimal.valueOf(location.getY()),
                BigDecimal.valueOf(location.getX()),
                request.bearing(),
                request.speed(),
                updatedAt
        );
    }

    private LatestDriverLocationStore.LatestDriverLocation latestFrom(DriverLocationResponse response) {
        return new LatestDriverLocationStore.LatestDriverLocation(
                response.tripId(),
                response.driverId(),
                response.lat(),
                response.lng(),
                response.bearing(),
                response.speed(),
                response.updatedAt()
        );
    }

    private Point toPoint(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Latitude and longitude are required");
        }
        return GEOMETRY_FACTORY.createPoint(new Coordinate(longitude.doubleValue(), latitude.doubleValue()));
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
