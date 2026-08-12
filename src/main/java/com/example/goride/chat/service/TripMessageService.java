package com.example.goride.chat.service;

import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.domain.TripStatus;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.chat.domain.TripMessage;
import com.example.goride.chat.domain.TripMessageSenderRole;
import com.example.goride.chat.dto.TripMessageCreateRequest;
import com.example.goride.chat.dto.TripMessageResponse;
import com.example.goride.chat.dto.TripMessageSendRequest;
import com.example.goride.chat.dto.TripMessageSyncMode;
import com.example.goride.chat.dto.TripMessageSyncResponse;
import com.example.goride.chat.repository.TripMessageRepository;
import com.example.goride.common.api.PageResponse;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.user.domain.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
public class TripMessageService {
    private static final Logger log = LoggerFactory.getLogger(TripMessageService.class);
    private static final Set<TripStatus> SENDABLE_STATUSES = Set.of(
            TripStatus.ACCEPTED,
            TripStatus.ARRIVED,
            TripStatus.IN_PROGRESS
    );

    private final TripRepository tripRepository;
    private final TripMessageRepository tripMessageRepository;
    private final TripMessageRealtimeNotifier tripMessageRealtimeNotifier;
    private final TripMessageRateLimiter tripMessageRateLimiter;
    private final TripMessagePushNotifier tripMessagePushNotifier;

    public TripMessageService(
            TripRepository tripRepository,
            TripMessageRepository tripMessageRepository,
            TripMessageRealtimeNotifier tripMessageRealtimeNotifier,
            TripMessageRateLimiter tripMessageRateLimiter,
            TripMessagePushNotifier tripMessagePushNotifier
    ) {
        this.tripRepository = tripRepository;
        this.tripMessageRepository = tripMessageRepository;
        this.tripMessageRealtimeNotifier = tripMessageRealtimeNotifier;
        this.tripMessageRateLimiter = tripMessageRateLimiter;
        this.tripMessagePushNotifier = tripMessagePushNotifier;
    }

    @Transactional
    public TripMessageResponse sendMessage(Long senderId, Long tripId, TripMessageCreateRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Message body is required");
        }
        return sendMessage(senderId, new TripMessageSendRequest(tripId, request.clientMessageId(), request.body()));
    }

    @Transactional
    public TripMessageResponse sendMessage(Long senderId, TripMessageSendRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Message request is required");
        }
        if (request.tripId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Trip id is required");
        }

        if (request.clientMessageId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Client message id is required");
        }

        Trip trip = tripRepository.findActiveByIdForUpdate(request.tripId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
        TripMessageSenderRole senderRole = resolveSenderRole(trip, senderId);
        var existing = tripMessageRepository.findByTripIdAndSenderIdAndClientMessageId(
                trip.getId(),
                senderId,
                request.clientMessageId()
        );
        if (existing.isPresent()) {
            return TripMessageResponse.from(existing.get());
        }
        tripMessageRateLimiter.checkAllowed(senderId);
        if (!SENDABLE_STATUSES.contains(trip.getStatus())) {
            throw new BusinessException(
                    ErrorCode.TRIP_MESSAGE_NOT_AVAILABLE,
                    "Trip messages can only be sent after a driver accepts and before trip completion"
            );
        }

        TripMessage message = TripMessage.create(
                trip,
                sender(trip, senderRole),
                senderRole,
                request.clientMessageId(),
                request.body()
        );
        TripMessage saved = tripMessageRepository.save(message);
        TripMessageResponse response = TripMessageResponse.from(saved);
        Long recipientId = senderRole == TripMessageSenderRole.PASSENGER
                ? trip.getDriver().getId()
                : trip.getPassenger().getId();
        runAfterCommit(() -> tripMessageRealtimeNotifier.broadcastTripMessage(trip.getId(), response));
        runAfterCommit(() -> tripMessagePushNotifier.notifyRecipient(recipientId, response));
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

    @Transactional(readOnly = true)
    public TripMessageSyncResponse syncMessages(
            Long userId,
            boolean admin,
            Long tripId,
            Long beforeId,
            Long afterId,
            int limit
    ) {
        if (beforeId != null && afterId != null) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Only one of beforeId or afterId can be provided"
            );
        }
        if (limit < 1 || limit > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Limit must be between 1 and 100");
        }

        Trip trip = requireReadableTrip(userId, admin, tripId);
        var pageable = PageRequest.of(0, limit + 1);
        List<TripMessage> loaded;
        TripMessageSyncMode mode;
        if (afterId != null) {
            validateCursor(afterId, "afterId");
            loaded = tripMessageRepository.findByTripIdAndIdGreaterThanOrderByIdAsc(
                    trip.getId(),
                    afterId,
                    pageable
            );
            mode = TripMessageSyncMode.NEWER;
        } else if (beforeId != null) {
            validateCursor(beforeId, "beforeId");
            loaded = tripMessageRepository.findByTripIdAndIdLessThanOrderByIdDesc(
                    trip.getId(),
                    beforeId,
                    pageable
            );
            mode = TripMessageSyncMode.OLDER;
        } else {
            loaded = tripMessageRepository.findByTripIdOrderByIdDesc(trip.getId(), pageable);
            mode = TripMessageSyncMode.INITIAL;
        }

        boolean hasMore = loaded.size() > limit;
        List<TripMessage> page = new ArrayList<>(loaded.subList(0, Math.min(loaded.size(), limit)));
        if (mode != TripMessageSyncMode.NEWER) {
            Collections.reverse(page);
        }
        List<TripMessageResponse> items = page.stream().map(TripMessageResponse::from).toList();
        Long nextCursor = items.isEmpty()
                ? null
                : mode == TripMessageSyncMode.NEWER
                        ? items.get(items.size() - 1).id()
                        : items.get(0).id();
        return new TripMessageSyncResponse(items, mode, hasMore, nextCursor);
    }

    private Trip requireReadableTrip(Long userId, boolean admin, Long tripId) {
        Trip trip = tripRepository.findByIdAndDeletedAtIsNull(tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
        if (!admin && !isPassenger(trip, userId) && !isAssignedDriver(trip, userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only trip participants can view trip messages");
        }
        return trip;
    }

    private void validateCursor(Long cursor, String fieldName) {
        if (cursor <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " must be positive");
        }
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
        Runnable safeAction = () -> {
            try {
                action.run();
            } catch (RuntimeException exception) {
                log.warn("Trip message realtime delivery failed after database commit", exception);
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
