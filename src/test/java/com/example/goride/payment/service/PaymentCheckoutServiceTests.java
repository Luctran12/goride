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
import com.example.goride.payment.provider.PaymentCheckoutSession;
import com.example.goride.payment.provider.PaymentProvider;
import com.example.goride.payment.provider.PaymentProviderRegistry;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentCheckoutServiceTests {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Mock
    private PaymentAccessService paymentAccessService;

    private PaymentCheckoutService service;

    @BeforeEach
    void setUp() {
        service = new PaymentCheckoutService(
                paymentAccessService,
                new PaymentProviderRegistry(List.of(new CashPaymentProvider()))
        );
    }

    @Test
    void returnsCashCheckoutWithoutRedirect() {
        Payment payment = paymentForTrip();
        when(paymentAccessService.requireTripPayment(10L, 99L)).thenReturn(payment);

        var response = service.getTripPaymentCheckout(10L, 99L);

        assertThat(response.paymentId()).isEqualTo(70L);
        assertThat(response.tripId()).isEqualTo(99L);
        assertThat(response.amount()).isEqualByComparingTo(BigDecimal.valueOf(20000));
        assertThat(response.method()).isEqualTo(PaymentMethod.CASH);
        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.provider()).isNull();
        assertThat(response.checkoutRequired()).isFalse();
        assertThat(response.checkoutUrl()).isNull();
        assertThat(response.expiresAt()).isNull();
    }

    @Test
    void rejectsCheckoutForCompletedPayment() {
        Payment payment = paymentForTrip();
        payment.markCompleted();
        when(paymentAccessService.requireTripPayment(10L, 99L)).thenReturn(payment);

        assertThatThrownBy(() -> service.getTripPaymentCheckout(10L, 99L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.PAYMENT_INVALID_STATUS)
                );
    }

    @Test
    void usesProviderCheckoutSessionForRedirectPaymentMethods() {
        Instant expiresAt = Instant.parse("2026-06-10T10:30:00Z");
        service = new PaymentCheckoutService(
                paymentAccessService,
                new PaymentProviderRegistry(List.of(providerWithCheckoutUrl(expiresAt)))
        );
        Payment payment = paymentForTrip();
        when(paymentAccessService.requireTripPayment(10L, 99L)).thenReturn(payment);

        var response = service.getTripPaymentCheckout(10L, 99L);

        assertThat(response.checkoutRequired()).isTrue();
        assertThat(response.checkoutUrl()).isEqualTo("https://pay.example/checkout/70");
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
    }

    private PaymentProvider providerWithCheckoutUrl(Instant expiresAt) {
        return new PaymentProvider() {
            @Override
            public PaymentMethod paymentMethod() {
                return PaymentMethod.CASH;
            }

            @Override
            public Payment createPendingPayment(Trip trip) {
                return Payment.createPending(trip);
            }

            @Override
            public PaymentCheckoutSession createCheckoutSession(Payment payment) {
                return new PaymentCheckoutSession(
                        true,
                        " https://pay.example/checkout/" + payment.getId() + " ",
                        expiresAt
                );
            }
        };
    }

    private Payment paymentForTrip() {
        Trip trip = completedTrip(passenger(10L), driver(20L));
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
