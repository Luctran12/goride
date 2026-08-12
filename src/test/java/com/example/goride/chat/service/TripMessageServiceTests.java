package com.example.goride.chat.service;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.booking.domain.PricingConfig;
import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.chat.domain.TripMessage;
import com.example.goride.chat.domain.TripMessageSenderRole;
import com.example.goride.chat.dto.TripMessageCreateRequest;
import com.example.goride.chat.dto.TripMessageSendRequest;
import com.example.goride.chat.repository.TripMessageRepository;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.driver.domain.VehicleType;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripMessageServiceTests {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final UUID CLIENT_MESSAGE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private TripRepository tripRepository;

    @Mock
    private TripMessageRepository tripMessageRepository;

    @Mock
    private TripMessageRealtimeNotifier tripMessageRealtimeNotifier;

    @Mock
    private TripMessageRateLimiter tripMessageRateLimiter;

    @Mock
    private TripMessagePushNotifier tripMessagePushNotifier;

    private TripMessageService service;

    @BeforeEach
    void setUp() {
        service = new TripMessageService(
                tripRepository,
                tripMessageRepository,
                tripMessageRealtimeNotifier,
                tripMessageRateLimiter,
                tripMessagePushNotifier
        );
    }

    @Test
    void passengerCanSendMessageToAcceptedTrip() {
        Trip trip = acceptedTrip();
        when(tripRepository.findActiveByIdForUpdate(99L)).thenReturn(Optional.of(trip));
        when(tripMessageRepository.save(any(TripMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.sendMessage(
                10L,
                99L,
                new TripMessageCreateRequest(CLIENT_MESSAGE_ID, "  I am waiting at gate A  ")
        );

        ArgumentCaptor<TripMessage> messageCaptor = ArgumentCaptor.forClass(TripMessage.class);
        verify(tripMessageRepository).save(messageCaptor.capture());
        verify(tripMessageRealtimeNotifier).broadcastTripMessage(99L, response);
        verify(tripMessagePushNotifier).notifyRecipient(20L, response);
        assertThat(response.tripId()).isEqualTo(99L);
        assertThat(response.senderId()).isEqualTo(10L);
        assertThat(response.senderRole()).isEqualTo(TripMessageSenderRole.PASSENGER);
        assertThat(response.body()).isEqualTo("I am waiting at gate A");
        assertThat(messageCaptor.getValue().getBody()).isEqualTo("I am waiting at gate A");
    }

    @Test
    void assignedDriverCanSendMessageFromStompRequest() {
        Trip trip = acceptedTrip();
        when(tripRepository.findActiveByIdForUpdate(99L)).thenReturn(Optional.of(trip));
        when(tripMessageRepository.save(any(TripMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.sendMessage(20L, new TripMessageSendRequest(99L, CLIENT_MESSAGE_ID, "I am arriving"));

        assertThat(response.senderId()).isEqualTo(20L);
        assertThat(response.senderRole()).isEqualTo(TripMessageSenderRole.DRIVER);
        verify(tripMessageRealtimeNotifier).broadcastTripMessage(99L, response);
        verify(tripMessagePushNotifier).notifyRecipient(10L, response);
    }

    @Test
    void rejectsMessageFromUserOutsideTrip() {
        Trip trip = acceptedTrip();
        when(tripRepository.findActiveByIdForUpdate(99L)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> service.sendMessage(
                30L,
                99L,
                new TripMessageCreateRequest(CLIENT_MESSAGE_ID, "Hello")
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN)
                );

        verifyNoInteractions(tripMessageRepository, tripMessageRealtimeNotifier, tripMessagePushNotifier);
    }

    @Test
    void returnsExistingMessageForIdempotentRetryWithoutBroadcastingAgain() {
        Trip trip = acceptedTrip();
        TripMessage existing = TripMessage.create(
                trip,
                trip.getPassenger(),
                TripMessageSenderRole.PASSENGER,
                CLIENT_MESSAGE_ID,
                "Original body"
        );
        ReflectionTestUtils.setField(existing, "id", 501L);
        when(tripRepository.findActiveByIdForUpdate(99L)).thenReturn(Optional.of(trip));
        when(tripMessageRepository.findByTripIdAndSenderIdAndClientMessageId(
                99L,
                10L,
                CLIENT_MESSAGE_ID
        )).thenReturn(Optional.of(existing));

        var response = service.sendMessage(
                10L,
                99L,
                new TripMessageCreateRequest(CLIENT_MESSAGE_ID, "Changed retry body")
        );

        assertThat(response.id()).isEqualTo(501L);
        assertThat(response.body()).isEqualTo("Original body");
        verify(tripMessageRepository, never()).save(any(TripMessage.class));
        verifyNoInteractions(tripMessageRealtimeNotifier, tripMessagePushNotifier);
    }

    @Test
    void syncsNewerMessagesInAscendingOrderWithCursor() {
        Trip trip = acceptedTrip();
        TripMessage first = message(trip, 501L, "First");
        TripMessage second = message(trip, 502L, "Second");
        when(tripRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.of(trip));
        when(tripMessageRepository.findByTripIdAndIdGreaterThanOrderByIdAsc(eq(99L), eq(500L), any(Pageable.class)))
                .thenReturn(List.of(first, second));

        var response = service.syncMessages(10L, false, 99L, null, 500L, 10);

        assertThat(response.items()).extracting(item -> item.id()).containsExactly(501L, 502L);
        assertThat(response.nextCursor()).isEqualTo(502L);
        assertThat(response.hasMore()).isFalse();
    }

    @Test
    void rejectsMessageBeforeDriverAcceptsTrip() {
        Trip trip = sampleTrip();
        when(tripRepository.findActiveByIdForUpdate(99L)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> service.sendMessage(
                10L,
                99L,
                new TripMessageCreateRequest(CLIENT_MESSAGE_ID, "Hello")
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.TRIP_MESSAGE_NOT_AVAILABLE)
                );

        verify(tripMessageRepository).findByTripIdAndSenderIdAndClientMessageId(99L, 10L, CLIENT_MESSAGE_ID);
        verify(tripMessageRepository, never()).save(any(TripMessage.class));
        verifyNoInteractions(tripMessageRealtimeNotifier, tripMessagePushNotifier);
    }

    @Test
    void participantCanListMessages() {
        Trip trip = acceptedTrip();
        TripMessage message = TripMessage.create(
                trip,
                trip.getDriver(),
                TripMessageSenderRole.DRIVER,
                CLIENT_MESSAGE_ID,
                "I am arriving"
        );
        when(tripRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.of(trip));
        when(tripMessageRepository.findLatestByTripId(eq(99L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(message)));

        var response = service.listMessages(10L, false, 99L, 1, 50);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).senderRole()).isEqualTo(TripMessageSenderRole.DRIVER);
        assertThat(response.items().get(0).body()).isEqualTo("I am arriving");
        assertThat(response.pagination().totalItems()).isEqualTo(1);
    }

    @Test
    void adminCanListMessagesForSupport() {
        Trip trip = acceptedTrip();
        when(tripRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.of(trip));
        when(tripMessageRepository.findLatestByTripId(eq(99L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        var response = service.listMessages(1L, true, 99L, 1, 50);

        assertThat(response.items()).isEmpty();
        verify(tripMessageRepository).findLatestByTripId(eq(99L), any(Pageable.class));
    }

    private Trip acceptedTrip() {
        Trip trip = sampleTrip();
        trip.accept(driver(20L));
        return trip;
    }

    private TripMessage message(Trip trip, Long id, String body) {
        TripMessage message = TripMessage.create(
                trip,
                trip.getDriver(),
                TripMessageSenderRole.DRIVER,
                UUID.randomUUID(),
                body
        );
        ReflectionTestUtils.setField(message, "id", id);
        return message;
    }

    private Trip sampleTrip() {
        Trip trip = Trip.create(
                passenger(10L),
                VehicleType.MOTORBIKE,
                PaymentMethod.CASH,
                "Pickup",
                point(106.7000, 10.7700),
                "Dropoff",
                point(106.7100, 10.7800),
                BigDecimal.valueOf(4.2),
                18,
                BigDecimal.valueOf(32000),
                PricingConfig.create(
                        VehicleType.MOTORBIKE,
                        BigDecimal.valueOf(10000),
                        BigDecimal.valueOf(4000),
                        BigDecimal.valueOf(300),
                        BigDecimal.valueOf(15000),
                        BigDecimal.ONE,
                        Instant.parse("2026-01-01T00:00:00Z")
                )
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
