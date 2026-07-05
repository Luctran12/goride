package com.example.goride.booking.service;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.booking.domain.PricingConfig;
import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.domain.TripStatus;
import com.example.goride.booking.domain.TripStatusHistory;
import com.example.goride.booking.event.BookingCreatedEvent;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.booking.repository.TripStatusHistoryRepository;
import com.example.goride.driver.domain.VehicleType;
import com.example.goride.notification.dto.TripStatusNotification;
import com.example.goride.notification.service.TripRealtimeNotifier;
import com.example.goride.user.domain.User;
import com.example.goride.user.domain.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledRideDispatchServiceTests {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Mock
    private TripRepository tripRepository;

    @Mock
    private TripStatusHistoryRepository tripStatusHistoryRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private TripRealtimeNotifier tripRealtimeNotifier;

    @Spy
    private ScheduledRideProperties scheduledRideProperties = new ScheduledRideProperties();

    @Mock
    private Clock clock;

    @InjectMocks
    private ScheduledRideDispatchService dispatchService;

    @Test
    void dispatchDueScheduledTripsOpensTripsForMatchingAndPublishesBookingEvent() {
        Instant now = Instant.parse("2026-07-04T10:00:00Z");
        Instant scheduledPickupTime = now.plusSeconds(600);
        Trip trip = withTripId(sampleScheduledTrip(scheduledPickupTime), 99L);
        when(clock.instant()).thenReturn(now);
        when(tripRepository.findReadyScheduledTripsForUpdate(
                eq(TripStatus.SCHEDULED),
                eq(now.plusSeconds(600)),
                any(Pageable.class)
        )).thenReturn(List.of(trip));
        when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int dispatched = dispatchService.dispatchDueScheduledTrips();

        ArgumentCaptor<TripStatusHistory> historyCaptor = ArgumentCaptor.forClass(TripStatusHistory.class);
        ArgumentCaptor<BookingCreatedEvent> eventCaptor = ArgumentCaptor.forClass(BookingCreatedEvent.class);
        ArgumentCaptor<TripStatusNotification> notificationCaptor = ArgumentCaptor.forClass(TripStatusNotification.class);
        verify(tripRepository).save(trip);
        verify(tripStatusHistoryRepository).save(historyCaptor.capture());
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        verify(tripRealtimeNotifier).broadcastTripStatus(eq(99L), notificationCaptor.capture());
        assertThat(dispatched).isEqualTo(1);
        assertThat(trip.getStatus()).isEqualTo(TripStatus.SEARCHING);
        assertThat(historyCaptor.getValue().getFromStatus()).isEqualTo(TripStatus.SCHEDULED);
        assertThat(historyCaptor.getValue().getToStatus()).isEqualTo(TripStatus.SEARCHING);
        assertThat(historyCaptor.getValue().getChangedBy()).isNull();
        assertThat(historyCaptor.getValue().getNote()).isEqualTo("Scheduled booking opened for matching");
        assertThat(eventCaptor.getValue().tripId()).isEqualTo(99L);
        assertThat(notificationCaptor.getValue().status()).isEqualTo(TripStatus.SEARCHING);
    }

    @Test
    void dispatchDueScheduledTripsUsesConfiguredBatchSize() {
        Instant now = Instant.parse("2026-07-04T10:00:00Z");
        scheduledRideProperties.setDispatchBatchSize(25);
        when(clock.instant()).thenReturn(now);
        when(tripRepository.findReadyScheduledTripsForUpdate(
                eq(TripStatus.SCHEDULED),
                eq(now.plusSeconds(600)),
                any(Pageable.class)
        )).thenReturn(List.of());

        dispatchService.dispatchDueScheduledTrips();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(tripRepository).findReadyScheduledTripsForUpdate(
                eq(TripStatus.SCHEDULED),
                eq(now.plusSeconds(600)),
                pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(25);
    }

    @Test
    void dispatchDueScheduledTripsSkipsRepositoryWhenDisabled() {
        scheduledRideProperties.setEnabled(false);

        int dispatched = dispatchService.dispatchDueScheduledTrips();

        assertThat(dispatched).isZero();
        verifyNoInteractions(tripRepository, tripStatusHistoryRepository, eventPublisher, tripRealtimeNotifier);
        verify(clock, never()).instant();
    }

    private Trip sampleScheduledTrip(Instant scheduledPickupTime) {
        return Trip.createScheduled(
                User.create("Passenger", "0900000000", null, "hash", Set.of(UserRole.PASSENGER)),
                VehicleType.MOTORBIKE,
                PaymentMethod.CASH,
                "Pickup",
                GEOMETRY_FACTORY.createPoint(new Coordinate(106.7000, 10.7700)),
                "Dropoff",
                GEOMETRY_FACTORY.createPoint(new Coordinate(106.7100, 10.7800)),
                BigDecimal.valueOf(3.2),
                12,
                BigDecimal.valueOf(25000),
                samplePricingConfig(VehicleType.MOTORBIKE),
                scheduledPickupTime
        );
    }

    private PricingConfig samplePricingConfig(VehicleType vehicleType) {
        return PricingConfig.create(
                vehicleType,
                BigDecimal.valueOf(10000),
                BigDecimal.valueOf(4000),
                BigDecimal.valueOf(300),
                BigDecimal.valueOf(15000),
                BigDecimal.ONE,
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }

    private Trip withTripId(Trip trip, Long id) {
        ReflectionTestUtils.setField(trip, "id", id);
        return trip;
    }
}