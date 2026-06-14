package com.example.goride.notification.service;

import com.google.firebase.messaging.Message;

public interface FirebaseMessagingGateway {
    void initialize();

    String send(Message message);
}
