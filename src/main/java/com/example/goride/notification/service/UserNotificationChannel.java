package com.example.goride.notification.service;

import com.example.goride.notification.dto.UserNotification;

public interface UserNotificationChannel {
    String channelName();

    void send(Long userId, UserNotification notification);
}
