package com.example.goride.notification.service;

import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.notification.domain.Notification;
import com.example.goride.notification.domain.NotificationType;
import com.example.goride.notification.dto.UserNotification;
import com.example.goride.notification.repository.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationInboxServiceTests {
    @Mock
    private NotificationRepository notificationRepository;

    private NotificationInboxService service;

    @BeforeEach
    void setUp() {
        service = new NotificationInboxService(notificationRepository, new ObjectMapper());
    }

    @Test
    void saveInboxNotificationPersistsUserNotificationData() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            ReflectionTestUtils.setField(notification, "id", 50L);
            return notification;
        });

        var response = service.saveInboxNotification(10L, notification());

        assertThat(response.notificationId()).isEqualTo(50L);
        assertThat(response.type()).isEqualTo(NotificationType.TRIP_ACCEPTED);
        assertThat(response.title()).isEqualTo("Trip accepted");
        assertThat(response.data()).containsEntry("tripId", 99);
        assertThat(response.read()).isFalse();
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void listMyNotificationsUsesOneBasedPagination() {
        Notification notification = savedNotification(50L, false);
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(10L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(notification), PageRequest.of(0, 20), 1));

        var response = service.listMyNotifications(10L, 1, 20);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).notificationId()).isEqualTo(50L);
        assertThat(response.items().get(0).data()).containsEntry("tripId", 99);
        assertThat(response.pagination().page()).isEqualTo(1);
        assertThat(response.pagination().totalItems()).isEqualTo(1);
    }

    @Test
    void listMyNotificationsRejectsInvalidPaginationBeforeQuerying() {
        assertThatThrownBy(() -> service.listMyNotifications(10L, 0, 20))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR)
                );
        assertThatThrownBy(() -> service.listMyNotifications(10L, 1, 101))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR)
                );

        verifyNoInteractions(notificationRepository);
    }

    @Test
    void markReadMarksOwnedNotificationAsRead() {
        Notification notification = savedNotification(50L, false);
        when(notificationRepository.findByIdAndUserId(50L, 10L)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(notification)).thenReturn(notification);

        var response = service.markRead(10L, 50L);

        verify(notificationRepository).save(notification);
        assertThat(response.read()).isTrue();
        assertThat(response.readAt()).isNotNull();
    }

    @Test
    void markReadKeepsExistingReadTimestampWhenAlreadyRead() {
        Notification notification = savedNotification(50L, true);
        Instant readAt = notification.getReadAt();
        when(notificationRepository.findByIdAndUserId(50L, 10L)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(notification)).thenReturn(notification);

        var response = service.markRead(10L, 50L);

        assertThat(response.read()).isTrue();
        assertThat(response.readAt()).isEqualTo(readAt);
    }

    @Test
    void markReadRejectsNotificationOwnedByAnotherUser() {
        when(notificationRepository.findByIdAndUserId(50L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(10L, 50L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND)
                );

        verify(notificationRepository, never()).save(any());
    }

    private Notification savedNotification(Long id, boolean read) {
        Notification notification = Notification.create(
                10L,
                notification(),
                "{\"tripId\":99,\"status\":\"ACCEPTED\"}"
        );
        ReflectionTestUtils.setField(notification, "id", id);
        if (read) {
            notification.markRead();
        }
        return notification;
    }

    private UserNotification notification() {
        return new UserNotification(
                NotificationType.TRIP_ACCEPTED,
                "Trip accepted",
                "Your driver is on the way",
                Map.of("tripId", 99, "status", "ACCEPTED"),
                Instant.parse("2026-06-09T10:00:00Z")
        );
    }
}
