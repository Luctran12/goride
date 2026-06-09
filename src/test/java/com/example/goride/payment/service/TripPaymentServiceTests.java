package com.example.goride.payment.service;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.booking.domain.PricingConfig;
import com.example.goride.booking.domain.Trip;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.driver.domain.VehicleType;
import com.example.goride.payment.domain.Payment;
import com.example.goride.payment.domain.PaymentStatus;
import com.example.goride.payment.provider.CashPaymentProvider;
import com.example.goride.payment.provider.PaymentProvider;
import com.example.goride.payment.repository.PaymentRepository;
import com.example.goride.user.domain.User;
import com.example.goride.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripPaymentServiceTests {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Mock
    private PaymentRepository paymentRepository;

    private TripPaymentService service;

    @BeforeEach
    void setUp() {
        service = new TripPaymentService(paymentRepository, List.of(new CashPaymentProvider()));
    }

    @Test
    void createsPendingPaymentForCompletedTrip() {
        Trip trip = completedTrip();
        when(paymentRepository.findByTripId(99L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment payment = service.createPendingPayment(trip);

        verify(paymentRepository).save(payment);
        assertThat(payment.getTrip()).isSameAs(trip);
        assertThat(payment.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(20000));
        assertThat(payment.getMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void returnsExistingPaymentWithoutCreatingDuplicate() {
        Trip trip = completedTrip();
        Payment existingPayment = Payment.createPending(trip);
        when(paymentRepository.findByTripId(99L)).thenReturn(Optional.of(existingPayment));

        Payment payment = service.createPendingPayment(trip);

        assertThat(payment).isSameAs(existingPayment);
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void rejectsPaymentMethodWithoutRegisteredProvider() {
        Trip trip = completedTrip();
        TripPaymentService serviceWithoutProvider = new TripPaymentService(paymentRepository, List.of());
        when(paymentRepository.findByTripId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceWithoutProvider.createPendingPayment(trip))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.PAYMENT_INVALID_STATUS)
                );

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void rejectsDuplicatePaymentProviderForMethod() {
        PaymentProvider firstProvider = new CashPaymentProvider();
        PaymentProvider secondProvider = new CashPaymentProvider();

        assertThatThrownBy(() ->
                        new TripPaymentService(paymentRepository, List.of(firstProvider, secondProvider)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate payment provider");
    }

    private Trip completedTrip() {
        Trip trip = sampleTrip();
        trip.accept(driver(20L));
        trip.markArrived();
        trip.startTrip();
        trip.complete(BigDecimal.valueOf(20000), BigDecimal.ONE, 20);
        return trip;
    }

    private Trip sampleTrip() {
        PricingConfig pricingConfig = PricingConfig.create(
                VehicleType.MOTORBIKE,
                BigDecimal.valueOf(10000),
                BigDecimal.valueOf(4000),
                BigDecimal.valueOf(300),
                BigDecimal.valueOf(15000),
                BigDecimal.ONE,
                Instant.parse("2026-01-01T00:00:00Z")
        );
        Trip trip = Trip.create(
                passenger(10L),
                VehicleType.MOTORBIKE,
                PaymentMethod.CASH,
                "Ben Thanh Market",
                point(106.7000, 10.7700),
                "Tan Son Nhat Airport",
                point(106.6650, 10.8130),
                BigDecimal.valueOf(4.2),
                18,
                BigDecimal.valueOf(32000),
                pricingConfig
        );
        ReflectionTestUtils.setField(trip, "id", 99L);
        return trip;
    }

    private User passenger(Long id) {
        User user = User.create("Passenger", "0900000000", null, "hash", Set.of(UserRole.PASSENGER));
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private User driver(Long id) {
        User user = User.create("Driver", "0900000001", null, "hash", Set.of(UserRole.DRIVER));
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static org.locationtech.jts.geom.Point point(double longitude, double latitude) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
    }
}
