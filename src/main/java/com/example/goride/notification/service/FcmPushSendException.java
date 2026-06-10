package com.example.goride.notification.service;

public class FcmPushSendException extends RuntimeException {
    public FcmPushSendException(String message, Throwable cause) {
        super(message, cause);
    }

    public FcmPushSendException(String message) {
        super(message);
    }
}
