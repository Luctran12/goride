package com.example.goride.notification.service;

import com.example.goride.notification.dto.FcmPushMessage;

public interface FcmPushSender {
    void send(FcmPushMessage message);
}
