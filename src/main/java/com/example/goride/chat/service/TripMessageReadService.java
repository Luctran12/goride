package com.example.goride.chat.service;

import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.chat.domain.TripMessage;
import com.example.goride.chat.domain.TripMessageReadState;
import com.example.goride.chat.dto.TripMessageReadRequest;
import com.example.goride.chat.dto.TripMessageReadStateResponse;
import com.example.goride.chat.dto.TripMessageUnreadCountResponse;
import com.example.goride.chat.repository.TripMessageReadStateRepository;
import com.example.goride.chat.repository.TripMessageRepository;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.user.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;

@Service
public class TripMessageReadService {
    private static final Logger log = LoggerFactory.getLogger(TripMessageReadService.class);

    private final TripRepository tripRepository;
    private final TripMessageRepository tripMessageRepository;
    private final TripMessageReadStateRepository readStateRepository;
    private final TripMessageRealtimeNotifier realtimeNotifier;

    public TripMessageReadService(
            TripRepository tripRepository,
            TripMessageRepository tripMessageRepository,
            TripMessageReadStateRepository readStateRepository,
            TripMessageRealtimeNotifier realtimeNotifier
    ) {
        this.tripRepository = tripRepository;
        this.tripMessageRepository = tripMessageRepository;
        this.readStateRepository = readStateRepository;
        this.realtimeNotifier = realtimeNotifier;
    }

    @Transactional
    public TripMessageReadStateResponse markRead(Long userId, Long tripId, TripMessageReadRequest request) {
        if (request == null || request.lastReadMessageId() == null || request.lastReadMessageId() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Last read message id must be positive");
        }

        Trip trip = tripRepository.findActiveByIdForUpdate(tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
        User participant = requireParticipant(trip, userId);
        TripMessage message = tripMessageRepository.findByIdAndTripId(request.lastReadMessageId(), tripId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "Last read message does not belong to this trip"
                ));

        TripMessageReadState state = readStateRepository.findByTripIdAndUserId(tripId, userId)
                .orElseGet(() -> TripMessageReadState.create(trip, participant, message));
        boolean advanced = state.getId() == null
                || state.getLastReadMessage().getId() < message.getId();
        state.advanceTo(message);
        TripMessageReadState saved = readStateRepository.save(state);
        long unreadCount = unreadCount(tripId, userId, saved.getLastReadMessage().getId());
        TripMessageReadStateResponse response = TripMessageReadStateResponse.from(saved, unreadCount);
        if (advanced) {
            runAfterCommit(() -> realtimeNotifier.broadcastReadState(tripId, response));
        }
        return response;
    }

    @Transactional(readOnly = true)
    public TripMessageUnreadCountResponse unreadCount(Long userId, Long tripId) {
        Trip trip = tripRepository.findByIdAndDeletedAtIsNull(tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
        requireParticipant(trip, userId);
        Long lastReadMessageId = readStateRepository.findByTripIdAndUserId(tripId, userId)
                .map(state -> state.getLastReadMessage().getId())
                .orElse(null);
        long unreadCount = lastReadMessageId == null
                ? tripMessageRepository.countByTripIdAndSenderIdNot(tripId, userId)
                : unreadCount(tripId, userId, lastReadMessageId);
        return new TripMessageUnreadCountResponse(tripId, lastReadMessageId, unreadCount);
    }

    private long unreadCount(Long tripId, Long userId, Long lastReadMessageId) {
        return tripMessageRepository.countByTripIdAndIdGreaterThanAndSenderIdNot(
                tripId,
                lastReadMessageId,
                userId
        );
    }

    private User requireParticipant(Trip trip, Long userId) {
        if (Objects.equals(trip.getPassenger().getId(), userId)) {
            return trip.getPassenger();
        }
        if (trip.getDriver() != null && Objects.equals(trip.getDriver().getId(), userId)) {
            return trip.getDriver();
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "Only trip participants can update message read state");
    }

    private void runAfterCommit(Runnable action) {
        Runnable safeAction = () -> {
            try {
                action.run();
            } catch (RuntimeException exception) {
                log.warn("Trip message read-state broadcast failed after database commit", exception);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            safeAction.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                safeAction.run();
            }
        });
    }
}
