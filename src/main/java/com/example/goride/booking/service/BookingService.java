package com.example.goride.booking.service;

import com.example.goride.booking.domain.PricingConfig;
import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.domain.TripStatus;
import com.example.goride.booking.domain.TripStatusHistory;
import com.example.goride.booking.dto.BookingCancelRequest;
import com.example.goride.booking.dto.BookingCreateRequest;
import com.example.goride.booking.dto.BookingEstimateRequest;
import com.example.goride.booking.dto.BookingLocationRequest;
import com.example.goride.booking.dto.FareEstimateResponse;
import com.example.goride.booking.dto.TripResponse;
import com.example.goride.booking.event.BookingCreatedEvent;
import com.example.goride.booking.repository.PricingConfigRepository;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.booking.repository.TripStatusHistoryRepository;
import com.example.goride.booking.service.distance.DistanceEstimate;
import com.example.goride.booking.service.distance.DistanceService;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.user.domain.User;
import com.example.goride.user.domain.UserRole;
import com.example.goride.user.repository.UserRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class BookingService {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private final UserRepository userRepository;
    private final PricingConfigRepository pricingConfigRepository;
    private final TripRepository tripRepository;
    private final TripStatusHistoryRepository tripStatusHistoryRepository;
    private final DistanceService distanceService;
    private final ApplicationEventPublisher eventPublisher;

    public BookingService(
            UserRepository userRepository,
            PricingConfigRepository pricingConfigRepository,
            TripRepository tripRepository,
            TripStatusHistoryRepository tripStatusHistoryRepository,
            DistanceService distanceService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.userRepository = userRepository;
        this.pricingConfigRepository = pricingConfigRepository;
        this.tripRepository = tripRepository;
        this.tripStatusHistoryRepository = tripStatusHistoryRepository;
        this.distanceService = distanceService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public FareEstimateResponse estimateFare(BookingEstimateRequest request) {
        FareCalculation calculation = calculateFare(request);
        return FareEstimateResponse.of(
                request.vehicleType(),
                calculation.distanceEstimate().distanceKm(),
                calculation.distanceEstimate().durationMinutes(),
                calculation.estimatedFare()
        );
    }

    @Transactional
    public TripResponse createBooking(Long passengerId, BookingCreateRequest request) {
        User passenger = userRepository.findByIdAndDeletedAtIsNull(passengerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!passenger.hasRole(UserRole.PASSENGER)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only passenger users can create bookings");
        }
        if (tripRepository.existsByPassengerIdAndStatusInAndDeletedAtIsNull(passengerId, TripStatus.activeStatuses())) {
            throw new BusinessException(ErrorCode.PASSENGER_HAS_ACTIVE_TRIP);
        }

        FareCalculation calculation = calculateFare(request.toEstimateRequest());
        Trip trip = Trip.create(
                passenger,
                request.vehicleType(),
                request.paymentMethod(),
                request.pickup().address(),
                toPoint(request.pickup()),
                request.dropoff().address(),
                toPoint(request.dropoff()),
                calculation.distanceEstimate().distanceKm(),
                calculation.distanceEstimate().durationMinutes(),
                calculation.estimatedFare(),
                calculation.pricingConfig()
        );

        Trip savedTrip = tripRepository.save(trip);
        tripStatusHistoryRepository.save(TripStatusHistory.record(
                savedTrip,
                null,
                TripStatus.SEARCHING,
                passenger,
                "Booking created"
        ));
        publishAfterCommit(BookingCreatedEvent.from(savedTrip));
        return TripResponse.from(savedTrip);
    }

    @Transactional(readOnly = true)
    public TripResponse getMyBooking(Long currentUserId, Long tripId) {
        User user = getActiveUser(currentUserId);
        Trip trip = getActiveTrip(tripId);
        assertCanAccessTrip(user, trip);
        return TripResponse.from(trip);
    }

    @Transactional(readOnly = true)
    public List<TripResponse> listMyBookings(Long currentUserId) {
        User user = getActiveUser(currentUserId);
        Map<Long, Trip> tripsById = new LinkedHashMap<>();

        if (user.hasRole(UserRole.PASSENGER)) {
            tripRepository.findByPassengerIdAndDeletedAtIsNullOrderByRequestedAtDesc(currentUserId)
                    .forEach(trip -> tripsById.put(trip.getId(), trip));
        }
        if (user.hasRole(UserRole.DRIVER)) {
            tripRepository.findByDriverIdAndDeletedAtIsNullOrderByRequestedAtDesc(currentUserId)
                    .forEach(trip -> tripsById.put(trip.getId(), trip));
        }
        if (tripsById.isEmpty() && !user.hasRole(UserRole.PASSENGER) && !user.hasRole(UserRole.DRIVER)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        List<Trip> trips = new ArrayList<>(tripsById.values());
        trips.sort(Comparator.comparing(Trip::getRequestedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return trips.stream().map(TripResponse::from).toList();
    }

    @Transactional
    public TripResponse cancelBooking(Long currentUserId, Long tripId, BookingCancelRequest request) {
        User user = getActiveUser(currentUserId);
        Trip trip = getActiveTrip(tripId);
        assertCanAccessTrip(user, trip);
        if (!trip.getStatus().canBeCancelled()) {
            throw new BusinessException(ErrorCode.TRIP_CANNOT_BE_CANCELLED);
        }

        TripStatus previousStatus = trip.getStatus();
        trip.cancel(request.reason());
        Trip savedTrip = tripRepository.save(trip);
        tripStatusHistoryRepository.save(TripStatusHistory.record(
                savedTrip,
                previousStatus,
                TripStatus.CANCELLED,
                user,
                savedTrip.getCancelReason()
        ));
        return TripResponse.from(savedTrip);
    }

    private FareCalculation calculateFare(BookingEstimateRequest request) {
        PricingConfig pricingConfig = pricingConfigRepository
                .findFirstByVehicleTypeAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        request.vehicleType(),
                        Instant.now()
                )
                .orElseThrow(() -> new BusinessException(ErrorCode.PRICING_CONFIG_NOT_FOUND));
        DistanceEstimate distanceEstimate = distanceService.estimate(
                request.pickup().toLocation(),
                request.dropoff().toLocation()
        );
        BigDecimal estimatedFare = pricingConfig.estimateFare(
                distanceEstimate.distanceKm(),
                distanceEstimate.durationMinutes()
        );
        return new FareCalculation(pricingConfig, distanceEstimate, estimatedFare);
    }

    private User getActiveUser(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private Trip getActiveTrip(Long tripId) {
        return tripRepository.findByIdAndDeletedAtIsNull(tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
    }

    private void assertCanAccessTrip(User user, Trip trip) {
        if (user.hasRole(UserRole.ADMIN)
                || Objects.equals(user.getId(), trip.getPassenger().getId())
                || (trip.getDriver() != null && Objects.equals(user.getId(), trip.getDriver().getId()))) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private Point toPoint(BookingLocationRequest location) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(location.lng().doubleValue(), location.lat().doubleValue()));
    }

    private void publishAfterCommit(BookingCreatedEvent event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            eventPublisher.publishEvent(event);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eventPublisher.publishEvent(event);
            }
        });
    }

    private record FareCalculation(
            PricingConfig pricingConfig,
            DistanceEstimate distanceEstimate,
            BigDecimal estimatedFare
    ) {
    }
}
