package com.example.goride.notification.service;

import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.notification.dto.FcmTokenResponse;
import com.example.goride.notification.dto.FcmTokenUpdateRequest;
import org.springframework.stereotype.Service;

@Service
public class FcmDeviceTokenService {
    private static final int MAX_TOKEN_LENGTH = 4096;

    private final FcmDeviceTokenStore fcmDeviceTokenStore;

    public FcmDeviceTokenService(FcmDeviceTokenStore fcmDeviceTokenStore) {
        this.fcmDeviceTokenStore = fcmDeviceTokenStore;
    }

    public FcmTokenResponse updateToken(Long userId, FcmTokenUpdateRequest request) {
        validateUserId(userId);
        String token = normalizeToken(request);
        fcmDeviceTokenStore.saveToken(userId, token);
        return FcmTokenResponse.registered(userId);
    }

    public FcmTokenResponse deleteToken(Long userId) {
        validateUserId(userId);
        fcmDeviceTokenStore.deleteToken(userId);
        return FcmTokenResponse.deleted(userId);
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "User id is required");
        }
    }

    private String normalizeToken(FcmTokenUpdateRequest request) {
        if (request == null || request.token() == null || request.token().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "FCM token is required");
        }

        String token = request.token().strip();
        if (token.length() > MAX_TOKEN_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "FCM token must be at most 4096 characters");
        }
        return token;
    }
}
