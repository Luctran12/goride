package com.example.goride.booking.service;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.booking.domain.PricingConfig;
import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.domain.TripStatus;
import com.example.goride.booking.domain.TripStatusHistory;
import com.example.goride.booking.dto.BookingCancelRequest;
import com.example.goride.booking.dto.BookingCreateRequest;
import com.example.goride.booking.dto.BookingEstimateRequest;
import com.example.goride.booking.dto.BookingLocationRequest;
import com.example.goride.booking.event.BookingCancelledEvent;
import com.example.goride.booking.event.BookingCreatedEvent;
import com.example.goride.booking.repository.PricingConfigRepository;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.booking.repository.TripStatusHistoryRepository;
import com.example.goride.booking.service.distance.DistanceEstimate;
import com.example.goride.booking.service.distance.DistanceService;
import com.example.goride.booking.service.distance.Location;
import com.example.goride.booking.service.SurgePricingService.SurgePricingQuote;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.driver.domain.VehicleType;
import com.example.goride.payment.service.PaymentMethodService;
import com.example.goride.servicearea.service.ServiceAreaService;
import com.example.goride.user.domain.User;
import com.example.goride.user.domain.UserRole;
import com.example.goride.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTests {
    @Mock
    private UserRepository userRepository;

    @Mock
    private PricingConfigRepository pricingConfigRepository;

    @Mock
    private TripRepository tripRepository;

    @Mock
    private TripStatusHistoryRepository tripStatusHistoryRepository;

    @Mock
    private DistanceService distanceService;

    @Mock
    private PaymentMethodService paymentMethodService;

    @Mock
    private SurgePricingService surgePricingService;

    @Mock
    private ServiceAreaService serviceAreaService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Spy
    private ScheduledRideProperties scheduledRideProperties = new ScheduledRideProperties();

    @Mock
    private Clock clock;

    @InjectMocks
    private BookingService bookingService;

    @Test
    void estimateFareUsesActivePricingAndDistanceService() {
        when(pricingConfigRepository
                .findFirstByVehicleTypeAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        any(),
                        any()
                ))
                .thenReturn(Optional.of(pricingConfig()));
        when(distanceService.estimate(any(Location.class), any(Location.class)))
                .thenReturn(new DistanceEstimate(BigDecimal.valueOf(5.5), 20));
        when(surgePricingService.quote(any(PricingConfig.class), eq(true)))
                .thenReturn(surgeQuote(BigDecimal.ONE, BigDecimal.ONE));

        var response = bookingService.estimateFare(estimateRequest());

        assertThat(response.vehicleType()).isEqualTo(VehicleType.MOTORBIKE);
        assertThat(response.distanceKm()).isEqualByComparingTo(BigDecimal.valueOf(5.5));
        assertThat(response.durationMinutes()).isEqualTo(20);
        assertThat(response.estimatedFare()).isEqualByComparingTo(BigDecimal.valueOf(38000));
        assertThat(response.baseFare()).isEqualByComparingTo(BigDecimal.valueOf(38000));
        assertThat(response.dynamicSurgeMultiplier()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(response.effectiveSurgeMultiplier()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(response.surgeAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.currency()).isEqualTo("VND");
    }

    @Test
    void estimateFareAppliesDynamicSurgeQuote() {
        when(pricingConfigRepository
                .findFirstByVehicleTypeAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        any(),
                        any()
                ))
                .thenReturn(Optional.of(pricingConfig()));
        when(distanceService.estimate(any(Location.class), any(Location.class)))
                .thenReturn(new DistanceEstimate(BigDecimal.valueOf(5.5), 20));
        when(surgePricingService.quote(any(PricingConfig.class), eq(true)))
                .thenReturn(surgeQuote(BigDecimal.ONE, BigDecimal.valueOf(1.25)));

        var response = bookingService.estimateFare(estimateRequest());

        assertThat(response.baseFare()).isEqualByComparingTo(BigDecimal.valueOf(38000));
        assertThat(response.dynamicSurgeMultiplier()).isEqualByComparingTo(BigDecimal.valueOf(1.25));
        assertThat(response.effectiveSurgeMultiplier()).isEqualByComparingTo(BigDecimal.valueOf(1.25));
        assertThat(response.estimatedFare()).isEqualByComparingTo(BigDecimal.valueOf(47500));
        assertThat(response.surgeAmount()).isEqualByComparingTo(BigDecimal.valueOf(9500));
        assertThat(response.surge().ruleName()).isEqualTo("Peak demand");
    }

    @Test
    void createBookingPersistsTripHistoryAndPublishesEvent() {
        User passenger = withUserId(
                User.create("Passenger", "0900000000", null, "hash", Set.of(UserRole.PASSENGER)),
                10L
        );
        when(userRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(passenger));
        when(pricingConfigRepository
                .findFirstByVehicleTypeAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        any(),
                        any()
                ))
                .thenReturn(Optional.of(pricingConfig()));
        when(distanceService.estimate(any(Location.class), any(Location.class)))
                .thenReturn(new DistanceEstimate(BigDecimal.valueOf(5.5), 20));
        when(surgePricingService.quote(any(PricingConfig.class), eq(true)))
                .thenReturn(surgeQuote(BigDecimal.ONE, BigDecimal.valueOf(1.25)));
        when(paymentMethodService.isPaymentMethodEnabled(PaymentMethod.CASH)).thenReturn(true);
        when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> withTripId(invocation.getArgument(0), 99L));

        var response = bookingService.createBooking(10L, createRequest());

        ArgumentCaptor<Trip> tripCaptor = ArgumentCaptor.forClass(Trip.class);
        ArgumentCaptor<TripStatusHistory> historyCaptor = ArgumentCaptor.forClass(TripStatusHistory.class);
        ArgumentCaptor<BookingCreatedEvent> eventCaptor = ArgumentCaptor.forClass(BookingCreatedEvent.class);
        verify(tripRepository).save(tripCaptor.capture());
        verify(tripStatusHistoryRepository).save(historyCaptor.capture());
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        assertThat(response.id()).isEqualTo(99L);
        assertThat(response.passengerId()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo(TripStatus.SEARCHING);
        assertThat(response.estimatedFare()).isEqualByComparingTo(BigDecimal.valueOf(47500));
        assertThat(tripCaptor.getValue().getPassenger()).isSameAs(passenger);
        assertThat(tripCaptor.getValue().getFareSurgeMultiplier()).isEqualByComparingTo(BigDecimal.valueOf(1.25));
        assertThat(historyCaptor.getValue().getToStatus()).isEqualTo(TripStatus.SEARCHING);
        assertThat(historyCaptor.getValue().getChangedBy()).isSameAs(passenger);
        assertThat(eventCaptor.getValue().tripId()).isEqualTo(99L);
        assertThat(eventCaptor.getValue().passengerId()).isEqualTo(10L);
    }

    @Test
    void createBookingRejectsNonPassengerUser() {
        User driver = withUserId(
                User.create("Driver", "0900000001", null, "hash", Set.of(UserRole.DRIVER)),
                10L
        );
        when(userRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(driver));

        assertThatThrownBy(() -> bookingService.createBooking(10L, createRequest()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN)
                );
        verify(tripRepository, never()).save(any(Trip.class));
    }

    @Test
    void createBookingRejectsPassengerWithActiveTrip() {
        User passenger = withUserId(
                User.create("Passenger", "0900000000", null, "hash", Set.of(UserRole.PASSENGER)),
                10L
        );
        when(userRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(passenger));
        when(tripRepository.existsByPassengerIdAndStatusInAndDeletedAtIsNull(10L, TripStatus.activeStatuses()))
                .thenReturn(true);

        assertThatThrownBy(() -> bookingService.createBooking(10L, createRequest()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.PASSENGER_HAS_ACTIVE_TRIP)
                );
        verify(tripRepository, never()).save(any(Trip.class));
    }

    @Test
    void createBookingRejectsUnavailablePaymentMethod() {
        User passenger = withUserId(
                User.create("Passenger", "0900000000", null, "hash", Set.of(UserRole.PASSENGER)),
                10L
        );
        when(userRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(passenger));
        when(paymentMethodService.isPaymentMethodEnabled(PaymentMethod.MOMO)).thenReturn(false);

        assertThatThrownBy(() -> bookingService.createBooking(10L, createRequest(PaymentMethod.MOMO)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.PAYMENT_PROVIDER_UNSUPPORTED)
                );
        verify(distanceService, never()).estimate(any(Location.class), any(Location.class));
        verify(tripRepository, never()).save(any(Trip.class));
    }

    @Test
    void estimateFareRequiresConfiguredPricing() {
        when(pricingConfigRepository
                .findFirstByVehicleTypeAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        any(),
                        any()
                ))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.estimateFare(estimateRequest()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.PRICING_CONFIG_NOT_FOUND)
                );
    }

    @Test
    void estimateFareRejectsLocationsOutsideServiceAreaBeforeDistancePricing() {
        doThrow(new BusinessException(ErrorCode.LOCATION_OUT_OF_SERVICE_AREA))
                .when(serviceAreaService)
                .validateTripWithinServiceArea(any(Location.class), any(Location.class));

        assertThatThrownBy(() -> bookingService.estimateFare(estimateRequest()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.LOCATION_OUT_OF_SERVICE_AREA)
                );

        verify(pricingConfigRepository, never())
                .findFirstByVehicleTypeAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(any(), any());
        verify(distanceService, never()).estimate(any(Location.class), any(Location.class));
    }

    @Test
    void getMyBookingReturnsTripForPassengerOwner() {
        User passenger = withUserId(
                User.create("Passenger", "0900000000", null, "hash", Set.of(UserRole.PASSENGER)),
                10L
        );
        Trip trip = withTripId(sampleTrip(passenger), 99L);
        when(userRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(passenger));
        when(tripRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.of(trip));

        var response = bookingService.getMyBooking(10L, 99L);

        assertThat(response.id()).isEqualTo(99L);
        assertThat(response.passengerId()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo(TripStatus.SEARCHING);
    }

    @Test
    void getMyBookingRejectsUnrelatedUser() {
        User passenger = withUserId(
                User.create("Passenger", "0900000000", null, "hash", Set.of(UserRole.PASSENGER)),
                10L
        );
        User otherPassenger = withUserId(
                User.create("Other", "0900000002", null, "hash", Set.of(UserRole.PASSENGER)),
                20L
        );
        Trip trip = withTripId(sampleTrip(otherPassenger), 99L);
        when(userRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(passenger));
        when(tripRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> bookingService.getMyBooking(10L, 99L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN)
                );
    }

    @Test
    void listMyBookingsCombinesPassengerAndDriverTripsNewestFirst() {
        User riderDriver = withUserId(
                User.create("Rider Driver", "0900000000", null, "hash", Set.of(UserRole.PASSENGER, UserRole.DRIVER)),
                10L
        );
        User passenger = withUserId(
                User.create("Passenger", "0900000002", null, "hash", Set.of(UserRole.PASSENGER)),
                20L
        );
        Trip passengerTrip = withTripId(sampleTrip(riderDriver), 99L);
        ReflectionTestUtils.setField(passengerTrip, "requestedAt", Instant.parse("2026-05-18T08:00:00Z"));
        Trip driverTrip = withTripId(sampleTrip(passenger), 100L);
        driverTrip.accept(riderDriver);
        ReflectionTestUtils.setField(driverTrip, "requestedAt", Instant.parse("2026-05-18T09:00:00Z"));
        when(userRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(riderDriver));
        when(tripRepository.findByPassengerIdAndDeletedAtIsNullOrderByRequestedAtDesc(10L))
                .thenReturn(List.of(passengerTrip));
        when(tripRepository.findByDriverIdAndDeletedAtIsNullOrderByRequestedAtDesc(10L))
                .thenReturn(List.of(driverTrip));

        var response = bookingService.listMyBookings(10L);

        assertThat(response).extracting("id").containsExactly(100L, 99L);
    }

    @Test
    void cancelBookingStoresCancelledStatusHistory() {
        User passenger = withUserId(
                User.create("Passenger", "0900000000", null, "hash", Set.of(UserRole.PASSENGER)),
                10L
        );
        Trip trip = withTripId(sampleTrip(passenger), 99L);
        when(userRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(passenger));
        when(tripRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.of(trip));
        when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = bookingService.cancelBooking(10L, 99L, new BookingCancelRequest(" Changed plan "));

        ArgumentCaptor<TripStatusHistory> historyCaptor = ArgumentCaptor.forClass(TripStatusHistory.class);
        ArgumentCaptor<BookingCancelledEvent> eventCaptor = ArgumentCaptor.forClass(BookingCancelledEvent.class);
        verify(tripStatusHistoryRepository).save(historyCaptor.capture());
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(response.status()).isEqualTo(TripStatus.CANCELLED);
        assertThat(response.cancelReason()).isEqualTo("Changed plan");
        assertThat(historyCaptor.getValue().getFromStatus()).isEqualTo(TripStatus.SEARCHING);
        assertThat(historyCaptor.getValue().getToStatus()).isEqualTo(TripStatus.CANCELLED);
        assertThat(historyCaptor.getValue().getChangedBy()).isSameAs(passenger);
        assertThat(eventCaptor.getValue().tripId()).isEqualTo(99L);
        assertThat(eventCaptor.getValue().passengerId()).isEqualTo(10L);
        assertThat(eventCaptor.getValue().driverId()).isNull();
        assertThat(eventCaptor.getValue().reason()).isEqualTo("Changed plan");
    }

    @Test
    void cancelBookingRejectsTripAlreadyInProgress() {
        User passenger = withUserId(
                User.create("Passenger", "0900000000", null, "hash", Set.of(UserRole.PASSENGER)),
                10L
        );
        User driver = withUserId(
                User.create("Driver", "0900000001", null, "hash", Set.of(UserRole.DRIVER)),
                20L
        );
        Trip trip = withTripId(sampleTrip(passenger), 99L);
        trip.accept(driver);
        trip.markArrived();
        trip.startTrip();
        when(userRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(passenger));
        when(tripRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> bookingService.cancelBooking(10L, 99L, new BookingCancelRequest("Too late")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.TRIP_CANNOT_BE_CANCELLED)
                );
        verify(tripRepository, never()).save(any(Trip.class));
        verify(tripStatusHistoryRepository, never()).save(any(TripStatusHistory.class));
    }

    private BookingEstimateRequest estimateRequest() {
        return new BookingEstimateRequest(
                new BookingLocationRequest(
                        BigDecimal.valueOf(10.7769),
                        BigDecimal.valueOf(106.7009),
                        "123 Le Loi, Q1"
                ),
                new BookingLocationRequest(
                        BigDecimal.valueOf(10.7850),
                        BigDecimal.valueOf(106.6800),
                        "456 CMT8, Q3"
                ),
                VehicleType.MOTORBIKE
        );
    }

    private BookingCreateRequest createRequest() {
        return createRequest(PaymentMethod.CASH);
    }

    private BookingCreateRequest createRequest(PaymentMethod paymentMethod) {
        return createRequest(paymentMethod, null);
    }

    private BookingCreateRequest createRequest(PaymentMethod paymentMethod, Instant scheduledPickupTime) {
        return new BookingCreateRequest(
                estimateRequest().pickup(),
                estimateRequest().dropoff(),
                VehicleType.MOTORBIKE,
                paymentMethod,
                scheduledPickupTime
        );
    }


    private SurgePricingQuote surgeQuote(BigDecimal pricingMultiplier, BigDecimal dynamicMultiplier) {
        BigDecimal effectiveMultiplier = pricingMultiplier.multiply(dynamicMultiplier);
        return new SurgePricingQuote(
                VehicleType.MOTORBIKE,
                3,
                1,
                BigDecimal.valueOf(3.00),
                pricingMultiplier,
                dynamicMultiplier,
                effectiveMultiplier,
                dynamicMultiplier.compareTo(BigDecimal.ONE) > 0 ? 10L : null,
                dynamicMultiplier.compareTo(BigDecimal.ONE) > 0 ? "Peak demand" : null
        );
    }
    private PricingConfig pricingConfig() {
        return PricingConfig.create(
                VehicleType.MOTORBIKE,
                BigDecimal.valueOf(10000),
                BigDecimal.valueOf(4000),
                BigDecimal.valueOf(300),
                BigDecimal.valueOf(15000),
                BigDecimal.ONE,
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }

    private Trip sampleTrip(User passenger) {
        org.locationtech.jts.geom.GeometryFactory geometryFactory = new org.locationtech.jts.geom.GeometryFactory(
                new org.locationtech.jts.geom.PrecisionModel(),
                4326
        );
        return Trip.create(
                passenger,
                VehicleType.MOTORBIKE,
                PaymentMethod.CASH,
                "123 Le Loi, Q1",
                geometryFactory.createPoint(new org.locationtech.jts.geom.Coordinate(106.7009, 10.7769)),
                "456 CMT8, Q3",
                geometryFactory.createPoint(new org.locationtech.jts.geom.Coordinate(106.6800, 10.7850)),
                BigDecimal.valueOf(5.5),
                20,
                BigDecimal.valueOf(38000),
                pricingConfig()
        );
    }

    private User withUserId(User user, Long id) {
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Trip withTripId(Trip trip, Long id) {
        ReflectionTestUtils.setField(trip, "id", id);
        return trip;
    }
}
