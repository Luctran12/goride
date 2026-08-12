package com.example.goride.chat.service;

import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.chat.domain.TripMessage;
import com.example.goride.chat.domain.TripMessageReadState;
import com.example.goride.chat.dto.TripMessageReadRequest;
import com.example.goride.chat.repository.TripMessageReadStateRepository;
import com.example.goride.chat.repository.TripMessageRepository;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class TripMessageReadServiceTests {
    @Mock
    private TripRepository tripRepository;
    @Mock
    private TripMessageRepository messageRepository;
    @Mock
    private TripMessageReadStateRepository readStateRepository;
    @Mock
    private TripMessageRealtimeNotifier realtimeNotifier;

    private TripMessageReadService service;

    @BeforeEach
    void setUp() {
        service = new TripMessageReadService(
                tripRepository,
                messageRepository,
                readStateRepository,
                realtimeNotifier
        );
    }

    @Test
    void participantAdvancesReadCursorAndReceivesUnreadCount() {
        User passenger = user(10L);
        Trip trip = trip(99L, passenger);
        TripMessage message = message(501L, trip);
        when(tripRepository.findActiveByIdForUpdate(99L)).thenReturn(Optional.of(trip));
        when(messageRepository.findByIdAndTripId(501L, 99L)).thenReturn(Optional.of(message));
        when(readStateRepository.findByTripIdAndUserId(99L, 10L)).thenReturn(Optional.empty());
        when(readStateRepository.save(any(TripMessageReadState.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.countByTripIdAndIdGreaterThanAndSenderIdNot(99L, 501L, 10L))
                .thenReturn(2L);

        var response = service.markRead(10L, 99L, new TripMessageReadRequest(501L));

        assertThat(response.lastReadMessageId()).isEqualTo(501L);
        assertThat(response.unreadCount()).isEqualTo(2L);
        verify(realtimeNotifier).broadcastReadState(99L, response);
    }

    @Test
    void rejectsReadCursorFromUserOutsideTrip() {
        Trip trip = trip(99L, user(10L));
        when(tripRepository.findActiveByIdForUpdate(99L)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> service.markRead(30L, 99L, new TripMessageReadRequest(501L)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN)
                );
    }

    private User user(Long id) {
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getId()).thenReturn(id);
        return user;
    }

    private Trip trip(Long id, User passenger) {
        Trip trip = org.mockito.Mockito.mock(Trip.class);
        lenient().when(trip.getId()).thenReturn(id);
        when(trip.getPassenger()).thenReturn(passenger);
        return trip;
    }

    private TripMessage message(Long id, Trip trip) {
        TripMessage message = org.mockito.Mockito.mock(TripMessage.class);
        when(message.getId()).thenReturn(id);
        when(message.getTrip()).thenReturn(trip);
        return message;
    }
}
