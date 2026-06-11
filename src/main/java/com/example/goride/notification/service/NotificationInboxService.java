package com.example.goride.notification.service;

import com.example.goride.common.api.PageResponse;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.notification.domain.Notification;
import com.example.goride.notification.dto.NotificationResponse;
import com.example.goride.notification.dto.UserNotification;
import com.example.goride.notification.repository.NotificationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class NotificationInboxService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    public NotificationInboxService(NotificationRepository notificationRepository, ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public NotificationResponse saveInboxNotification(Long userId, UserNotification userNotification) {
        validateUserId(userId);
        Notification notification = Notification.create(
                userId,
                userNotification,
                toJson(userNotification.data())
        );
        return toResponse(notificationRepository.save(notification));
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> listMyNotifications(Long userId, int page, int size) {
        validateUserId(userId);
        validatePageRequest(page, size);

        Page<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(
                userId,
                PageRequest.of(page - 1, size)
        );
        return PageResponse.of(
                notifications.getContent().stream()
                        .map(this::toResponse)
                        .toList(),
                page,
                size,
                notifications.getTotalElements()
        );
    }

    @Transactional
    public NotificationResponse markRead(Long userId, Long notificationId) {
        validateUserId(userId);
        if (notificationId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Notification id is required");
        }

        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markRead();
        return toResponse(notificationRepository.save(notification));
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "User id is required");
        }
    }

    private void validatePageRequest(int page, int size) {
        if (page < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Page must be greater than or equal to 1");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Size must be between 1 and 100");
        }
    }

    private NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.from(notification, fromJson(notification.getDataJson()));
    }

    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data == null ? Map.of() : data);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Notification data cannot be serialized", exception);
        }
    }

    private Map<String, Object> fromJson(String dataJson) {
        if (dataJson == null || dataJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(dataJson, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Notification data cannot be deserialized", exception);
        }
    }
}
