package com.example.goride.chat.service;

import com.example.goride.chat.dto.TripMessageResponse;

public interface TripMessagePushNotifier {
    void notifyRecipient(Long recipientId, TripMessageResponse message);
}
