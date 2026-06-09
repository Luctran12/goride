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
import com.example.goride.user.repository.UserRepository;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentQueryServiceTests {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private UserRepository userRepository;

    private PaymentQueryService service;

    @BeforeEach
    void setUp() {
        service = new PaymentQueryService(paymentRepository, userRepository);
    }

    @Test
    void returnsPaymentForPassengerOwner() {
        User passenger = passenger(10L);
        Payment payment = paymentForTrip(passenger, driver(20L));
        when(userRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(passenger));
        when(paymentRepository.findByTripIdWithTrip(99L)).thenReturn(Optional.of(payment));

        var response = service.getTripPayment(10L, 99L);

        assertThat(response.paymentId()).isEqualTo(70L);
        assertThat(response.tripId()).isEqualTo(99L);
        assertThat(response.amount()).isEqualByComparingTo(BigDecimal.valueOf(20000));
        assertThat(response.method()).isEqualTo(PaymentMethod.CASH);
        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void returnsPaymentForAssignedDriver() {
        User driver = driver(20L);
        Payment payment = paymentForTrip(passenger(10L), driver);
        when(userRepository.findByIdAndDeletedAtIsNull(20L)).thenReturn(Optional.of(driver));
        when(paymentRepository.findByTripIdWithTrip(99L)).thenReturn(Optional.of(payment));

        var response = service.getTripPayment(20L, 99L);

        assertThat(response.tripId()).isEqualTo(99L);
        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void returnsPaymentForAdmin() {
        User admin = user(30L, UserRole.ADMIN);
        Payment payment = paymentForTrip(passenger(10L), driver(20L));
        when(userRepository.findByIdAndDeletedAtIsNull(30L)).thenReturn(Optional.of(admin));
        when(paymentRepository.findByTripIdWithTrip(99L)).thenReturn(Optional.of(payment));

        var response = service.getTripPayment(30L, 99L);

        assertThat(response.tripId()).isEqualTo(99L);
        assertThat(response.amount()).isEqualByComparingTo(BigDecimal.valueOf(20000));
    }

    @Test
    void rejectsUnrelatedUser() {
        User otherPassenger = passenger(11L);
        Payment payment = paymentForTrip(passenger(10L), driver(20L));
        when(userRepository.findByIdAndDeletedAtIsNull(11L)).thenReturn(Optional.of(otherPassenger));
        when(paymentRepository.findByTripIdWithTrip(99L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.getTripPayment(11L, 99L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN)
                );
    }

    @Test
    void rejectsMissingCurrentUserBeforeQueryingPayment() {
        when(userRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTripPayment(10L, 99L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND)
                );

        verifyNoInteractions(paymentRepository);
    }

    @Test
    void rejectsMissingPayment() {
        User passenger = passenger(10L);
        when(userRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(passenger));
        when(paymentRepository.findByTripIdWithTrip(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTripPayment(10L, 99L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.PAYMENT_NOT_FOUND)
                );
    }

    private Payment paymentForTrip(User passenger, User driver) {
        Trip trip = completedTrip(passenger, driver);
        Payment payment = Payment.createPending(trip);
        ReflectionTestUtils.setField(payment, "id", 70L);
        return payment;
    }

    private Trip completedTrip(User passenger, User driver) {
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
                pricingConfig()
        );
        ReflectionTestUtils.setField(trip, "id", 99L);
        trip.accept(driver);
        trip.markArrived();
        trip.startTrip();
        trip.complete(BigDecimal.valueOf(20000), BigDecimal.ONE, 20);
        return trip;
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

    private User passenger(Long id) {
        return user(id, UserRole.PASSENGER);
    }

    private User driver(Long id) {
        return user(id, UserRole.DRIVER);
    }

    private User user(Long id, UserRole role) {
        User user = User.create(role.name(), "09000000" + id, null, "hash", Set.of(role));
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static org.locationtech.jts.geom.Point point(double longitude, double latitude) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
    }
}
