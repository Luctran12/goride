package com.example.goride.payment.service;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.booking.domain.PricingConfig;
import com.example.goride.booking.domain.Trip;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.driver.domain.VehicleType;
import com.example.goride.payment.domain.Payment;
import com.example.goride.payment.domain.PaymentStatus;
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
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CashPaymentConfirmationServiceTests {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentCompletionWorkflow paymentCompletionWorkflow;

    private CashPaymentConfirmationService service;

    @BeforeEach
    void setUp() {
        service = new CashPaymentConfirmationService(paymentRepository, paymentCompletionWorkflow);
    }

    @Test
    void confirmsPendingCashPaymentAndRunsCompletionWorkflow() {
        Trip trip = completedTrip(driver(20L));
        Payment payment = Payment.createPending(trip);
        when(paymentRepository.findByTripIdForUpdate(99L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(payment)).thenReturn(payment);

        var response = service.confirmCashPayment(20L, 99L);

        verify(paymentRepository).save(payment);
        verify(paymentCompletionWorkflow).handleCompletedPayment(payment);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getPaidAt()).isNotNull();
        assertThat(response.tripId()).isEqualTo(99L);
        assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(response.amount()).isEqualByComparingTo(BigDecimal.valueOf(20000));
        assertThat(response.paidAt()).isEqualTo(payment.getPaidAt());
    }

    @Test
    void rejectsMissingPayment() {
        when(paymentRepository.findByTripIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmCashPayment(20L, 99L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.PAYMENT_NOT_FOUND)
                );

        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(paymentCompletionWorkflow);
    }

    @Test
    void rejectsDriverThatDoesNotOwnTrip() {
        Trip trip = completedTrip(driver(20L));
        Payment payment = Payment.createPending(trip);
        when(paymentRepository.findByTripIdForUpdate(99L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.confirmCashPayment(21L, 99L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN)
                );

        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(paymentCompletionWorkflow);
    }

    @Test
    void rejectsAlreadyCompletedPayment() {
        Trip trip = completedTrip(driver(20L));
        Payment payment = Payment.createPending(trip);
        payment.markCompleted();
        when(paymentRepository.findByTripIdForUpdate(99L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.confirmCashPayment(20L, 99L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.PAYMENT_INVALID_STATUS)
                );

        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(paymentCompletionWorkflow);
    }

    @Test
    void rejectsPaymentWhenTripIsNotCompleted() {
        Trip trip = acceptedTrip(driver(20L));
        Payment payment = paymentForTripWithoutCompletion(trip);
        when(paymentRepository.findByTripIdForUpdate(99L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.confirmCashPayment(20L, 99L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.PAYMENT_INVALID_STATUS)
                );

        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(paymentCompletionWorkflow);
    }

    private Payment paymentForTripWithoutCompletion(Trip trip) {
        Payment payment = Payment.createPending(completedTrip(driver(20L)));
        ReflectionTestUtils.setField(payment, "trip", trip);
        return payment;
    }

    private Trip completedTrip(User driver) {
        Trip trip = acceptedTrip(driver);
        trip.markArrived();
        trip.startTrip();
        trip.complete(BigDecimal.valueOf(20000), BigDecimal.ONE, 20);
        return trip;
    }

    private Trip acceptedTrip(User driver) {
        Trip trip = sampleTrip();
        trip.accept(driver);
        return trip;
    }

    private Trip sampleTrip() {
        User passenger = passenger(10L);
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
                passenger,
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
