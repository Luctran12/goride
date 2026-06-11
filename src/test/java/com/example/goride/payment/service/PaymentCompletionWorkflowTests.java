package com.example.goride.payment.service;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.booking.domain.PricingConfig;
import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.domain.TripStatus;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.driver.domain.VehicleType;
import com.example.goride.matching.service.DriverCandidateStore;
import com.example.goride.notification.domain.NotificationType;
import com.example.goride.notification.dto.UserNotification;
import com.example.goride.notification.service.TripRealtimeNotifier;
import com.example.goride.payment.domain.Payment;
import com.example.goride.payment.domain.PaymentStatus;
import com.example.goride.user.domain.User;
import com.example.goride.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PaymentCompletionWorkflowTests {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Mock
    private DriverCandidateStore driverCandidateStore;

    @Mock
    private TripRealtimeNotifier tripRealtimeNotifier;

    private PaymentCompletionWorkflow workflow;

    @BeforeEach
    void setUp() {
        workflow = new PaymentCompletionWorkflow(driverCandidateStore, tripRealtimeNotifier);
    }

    @Test
    void marksDriverAvailableAndNotifiesPassengerAndDriver() {
        Payment payment = completedPayment();

        workflow.handleCompletedPayment(payment);

        verify(driverCandidateStore).markCandidateAvailable(20L);
        ArgumentCaptor<UserNotification> passengerNotification = ArgumentCaptor.forClass(UserNotification.class);
        ArgumentCaptor<UserNotification> driverNotification = ArgumentCaptor.forClass(UserNotification.class);
        verify(tripRealtimeNotifier).notifyPassenger(eq(10L), passengerNotification.capture());
        verify(tripRealtimeNotifier).notifyUser(eq(20L), driverNotification.capture());
        assertPaymentNotification(passengerNotification.getValue());
        assertPaymentNotification(driverNotification.getValue());
    }

    @Test
    void rejectsPaymentThatIsNotCompleted() {
        Payment payment = Payment.createPending(completedTrip(driver(20L)));

        assertThatThrownBy(() -> workflow.handleCompletedPayment(payment))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.PAYMENT_INVALID_STATUS)
                );

        verifyNoInteractions(driverCandidateStore, tripRealtimeNotifier);
    }

    private void assertPaymentNotification(UserNotification notification) {
        assertThat(notification.type()).isEqualTo(NotificationType.PAYMENT_COMPLETED);
        assertThat(notification.data())
                .containsEntry("tripId", 99L)
                .containsEntry("status", TripStatus.COMPLETED.name())
                .containsEntry("driverId", 20L)
                .containsEntry("amount", BigDecimal.valueOf(20000))
                .containsEntry("paymentStatus", PaymentStatus.COMPLETED.name());
    }

    private Payment completedPayment() {
        Payment payment = Payment.createPending(completedTrip(driver(20L)));
        payment.markCompleted();
        return payment;
    }

    private Trip completedTrip(User driver) {
        Trip trip = sampleTrip();
        trip.accept(driver);
        trip.markArrived();
        trip.startTrip();
        trip.complete(BigDecimal.valueOf(20000), BigDecimal.ONE, 20);
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
