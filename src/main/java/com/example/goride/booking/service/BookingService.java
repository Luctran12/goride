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
import com.example.goride.booking.event.BookingCancelledEvent;
import com.example.goride.booking.event.BookingCreatedEvent;
import com.example.goride.booking.repository.PricingConfigRepository;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.booking.repository.TripStatusHistoryRepository;
import com.example.goride.booking.service.distance.DistanceEstimate;
import com.example.goride.booking.service.distance.DistanceService;
import com.example.goride.booking.service.SurgePricingService.SurgePricingQuote;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.payment.service.PaymentMethodService;
import com.example.goride.servicearea.service.ServiceAreaService;
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
import java.time.Clock;
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
    private final PaymentMethodService paymentMethodService;
    private final SurgePricingService surgePricingService;
    private final ServiceAreaService serviceAreaService;
    private final ApplicationEventPublisher eventPublisher;
    private final ScheduledRideProperties scheduledRideProperties;
    private final Clock clock;

    public BookingService(
            UserRepository userRepository,
            PricingConfigRepository pricingConfigRepository,
            TripRepository tripRepository,
            TripStatusHistoryRepository tripStatusHistoryRepository,
            DistanceService distanceService,
            PaymentMethodService paymentMethodService,
            SurgePricingService surgePricingService,
            ServiceAreaService serviceAreaService,
            ApplicationEventPublisher eventPublisher,
            ScheduledRideProperties scheduledRideProperties,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.pricingConfigRepository = pricingConfigRepository;
        this.tripRepository = tripRepository;
        this.tripStatusHistoryRepository = tripStatusHistoryRepository;
        this.distanceService = distanceService;
        this.paymentMethodService = paymentMethodService;
        this.surgePricingService = surgePricingService;
        this.serviceAreaService = serviceAreaService;
        this.eventPublisher = eventPublisher;
        this.scheduledRideProperties = scheduledRideProperties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public FareEstimateResponse estimateFare(BookingEstimateRequest request) {
        FareCalculation calculation = calculateFare(request);
        return FareEstimateResponse.of(
                request.vehicleType(),
                calculation.distanceEstimate().distanceKm(),
                calculation.distanceEstimate().durationMinutes(),
                calculation.estimatedFare(),
                calculation.baseFare(),
                calculation.surgeQuote().pricingSurgeMultiplier(),
                calculation.surgeQuote().dynamicSurgeMultiplier(),
                calculation.surgeQuote().effectiveSurgeMultiplier(),
                calculation.surgeQuote().toResponse()
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
        if (!paymentMethodService.isPaymentMethodEnabled(request.paymentMethod())) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_PROVIDER_UNSUPPORTED,
                    "Payment method is not available: " + request.paymentMethod()
            );
        }
        validateScheduledPickupTime(request.scheduledPickupTime());

        FareCalculation calculation = calculateFare(request.toEstimateRequest());
        Trip trip = createTrip(passenger, request, calculation);

        Trip savedTrip = tripRepository.save(trip);
        TripStatus initialStatus = savedTrip.getStatus();
        tripStatusHistoryRepository.save(TripStatusHistory.record(
                savedTrip,
                null,
                initialStatus,
                passenger,
                initialStatus == TripStatus.SCHEDULED ? "Scheduled booking created" : "Booking created"
        ));
        if (initialStatus == TripStatus.SEARCHING) {
            publishAfterCommit(BookingCreatedEvent.from(savedTrip));
        }
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
        publishAfterCommit(BookingCancelledEvent.from(savedTrip));
        return TripResponse.from(savedTrip);
    }

    private Trip createTrip(User passenger, BookingCreateRequest request, FareCalculation calculation) {
        if (request.scheduledPickupTime() != null) {
            return Trip.createScheduled(
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
                    calculation.pricingConfig(),
                    calculation.surgeQuote().effectiveSurgeMultiplier(),
                    request.scheduledPickupTime()
            );
        }

        return Trip.create(
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
                calculation.pricingConfig(),
                calculation.surgeQuote().effectiveSurgeMultiplier()
        );
    }

    private void validateScheduledPickupTime(Instant scheduledPickupTime) {
        if (scheduledPickupTime == null) {
            return;
        }
        if (!scheduledRideProperties.isEnabled()) {
            throw new BusinessException(
                    ErrorCode.SCHEDULED_PICKUP_TIME_INVALID,
                    "Scheduled rides are not enabled"
            );
        }

        Instant earliestPickupTime = Instant.now(clock).plus(scheduledRideProperties.minLeadTime());
        if (scheduledPickupTime.isBefore(earliestPickupTime)) {
            throw new BusinessException(
                    ErrorCode.SCHEDULED_PICKUP_TIME_INVALID,
                    "Scheduled pickup time must be at least "
                            + scheduledRideProperties.getMinLeadTimeMinutes()
                            + " minutes in the future",
                    Map.of(
                            "earliestPickupTime", earliestPickupTime,
                            "minLeadTimeMinutes", scheduledRideProperties.getMinLeadTimeMinutes()
                    )
            );
        }
    }

    private FareCalculation calculateFare(BookingEstimateRequest request) {
        serviceAreaService.validateTripWithinServiceArea(
                request.pickup().toLocation(),
                request.dropoff().toLocation()
        );

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
        SurgePricingQuote surgeQuote = surgePricingService.quote(pricingConfig, true);
        BigDecimal baseFare = pricingConfig.baseFareAmount(
                distanceEstimate.distanceKm(),
                distanceEstimate.durationMinutes()
        );
        BigDecimal estimatedFare = pricingConfig.estimateFare(
                distanceEstimate.distanceKm(),
                distanceEstimate.durationMinutes(),
                surgeQuote.effectiveSurgeMultiplier()
        );
        return new FareCalculation(pricingConfig, distanceEstimate, baseFare, estimatedFare, surgeQuote);
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

    private void publishAfterCommit(Object event) {
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
            BigDecimal baseFare,
            BigDecimal estimatedFare,
            SurgePricingQuote surgeQuote
    ) {
    }
}
