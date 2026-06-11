package com.example.goride.notification.service;

public class FcmPushSendException extends RuntimeException {
    private final boolean invalidToken;

    public FcmPushSendException(String message, Throwable cause) {
        this(message, cause, false);
    }

    public FcmPushSendException(String message) {
        this(message, null, false);
    }

    private FcmPushSendException(String message, Throwable cause, boolean invalidToken) {
        super(message, cause);
        this.invalidToken = invalidToken;
    }

    public static FcmPushSendException invalidToken(String message, Throwable cause) {
        return new FcmPushSendException(message, cause, true);
    }

    public boolean isInvalidToken() {
        return invalidToken;
    }
}
