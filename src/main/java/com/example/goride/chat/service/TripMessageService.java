package com.example.goride.chat.service;

import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.domain.TripStatus;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.chat.domain.TripMessage;
import com.example.goride.chat.domain.TripMessageSenderRole;
import com.example.goride.chat.dto.TripMessageCreateRequest;
import com.example.goride.chat.dto.TripMessageResponse;
import com.example.goride.chat.dto.TripMessageSendRequest;
import com.example.goride.chat.repository.TripMessageRepository;
import com.example.goride.common.api.PageResponse;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.user.domain.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;
import java.util.Set;

@Service
public class TripMessageService {
    private static final Set<TripStatus> SENDABLE_STATUSES = Set.of(
            TripStatus.ACCEPTED,
            TripStatus.ARRIVED,
            TripStatus.IN_PROGRESS
    );

    private final TripRepository tripRepository;
    private final TripMessageRepository tripMessageRepository;
    private final TripMessageRealtimeNotifier tripMessageRealtimeNotifier;

    public TripMessageService(
            TripRepository tripRepository,
            TripMessageRepository tripMessageRepository,
            TripMessageRealtimeNotifier tripMessageRealtimeNotifier
    ) {
        this.tripRepository = tripRepository;
        this.tripMessageRepository = tripMessageRepository;
        this.tripMessageRealtimeNotifier = tripMessageRealtimeNotifier;
    }

    @Transactional
    public TripMessageResponse sendMessage(Long senderId, Long tripId, TripMessageCreateRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Message body is required");
        }
        return sendMessage(senderId, new TripMessageSendRequest(tripId, request.body()));
    }

    @Transactional
    public TripMessageResponse sendMessage(Long senderId, TripMessageSendRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Message request is required");
        }
        if (request.tripId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Trip id is required");
        }

        Trip trip = tripRepository.findByIdAndDeletedAtIsNull(request.tripId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
        if (!SENDABLE_STATUSES.contains(trip.getStatus())) {
            throw new BusinessException(
                    ErrorCode.TRIP_MESSAGE_NOT_AVAILABLE,
                    "Trip messages can only be sent after a driver accepts and before trip completion"
            );
        }

        TripMessageSenderRole senderRole = resolveSenderRole(trip, senderId);
        TripMessage message = TripMessage.create(trip, sender(trip, senderRole), senderRole, request.body());
        TripMessage saved = tripMessageRepository.save(message);
        TripMessageResponse response = TripMessageResponse.from(saved);
        runAfterCommit(() -> tripMessageRealtimeNotifier.broadcastTripMessage(trip.getId(), response));
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<TripMessageResponse> listMessages(Long userId, boolean admin, Long tripId, int page, int size) {
        Trip trip = tripRepository.findByIdAndDeletedAtIsNull(tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
        if (!admin && !isPassenger(trip, userId) && !isAssignedDriver(trip, userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only trip participants can view trip messages");
        }

        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        var pageable = PageRequest.of(
                safePage - 1,
                safeSize,
                Sort.by(Sort.Order.desc("sentAt"), Sort.Order.desc("id"))
        );
        var messages = tripMessageRepository.findLatestByTripId(tripId, pageable);
        return PageResponse.of(
                messages.stream()
                        .map(TripMessageResponse::from)
                        .toList(),
                safePage,
                safeSize,
                messages.getTotalElements()
        );
    }

    private TripMessageSenderRole resolveSenderRole(Trip trip, Long senderId) {
        if (isPassenger(trip, senderId)) {
            return TripMessageSenderRole.PASSENGER;
        }
        if (isAssignedDriver(trip, senderId)) {
            return TripMessageSenderRole.DRIVER;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "Only trip participants can send trip messages");
    }

    private User sender(Trip trip, TripMessageSenderRole senderRole) {
        return senderRole == TripMessageSenderRole.PASSENGER ? trip.getPassenger() : trip.getDriver();
    }

    private boolean isPassenger(Trip trip, Long userId) {
        return Objects.equals(trip.getPassenger().getId(), userId);
    }

    private boolean isAssignedDriver(Trip trip, Long userId) {
        return trip.getDriver() != null && Objects.equals(trip.getDriver().getId(), userId);
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
