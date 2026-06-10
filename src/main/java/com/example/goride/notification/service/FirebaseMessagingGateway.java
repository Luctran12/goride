package com.example.goride.notification.service;

import com.google.firebase.messaging.Message;

public interface FirebaseMessagingGateway {
    String send(Message message);
}
