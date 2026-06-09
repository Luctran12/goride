package com.example.goride.notification.controller;

import com.example.goride.common.api.ApiResponse;
import com.example.goride.common.security.CurrentUser;
import com.example.goride.notification.dto.FcmTokenResponse;
import com.example.goride.notification.dto.FcmTokenUpdateRequest;
import com.example.goride.notification.service.FcmDeviceTokenService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final FcmDeviceTokenService fcmDeviceTokenService;
    private final CurrentUser currentUser;

    public NotificationController(FcmDeviceTokenService fcmDeviceTokenService, CurrentUser currentUser) {
        this.fcmDeviceTokenService = fcmDeviceTokenService;
        this.currentUser = currentUser;
    }

    @PutMapping("/fcm-token")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<FcmTokenResponse> updateFcmToken(
            Authentication authentication,
            @Valid @RequestBody FcmTokenUpdateRequest request
    ) {
        return ApiResponse.ok(fcmDeviceTokenService.updateToken(
                currentUser.requireUserId(authentication),
                request
        ));
    }

    @DeleteMapping("/fcm-token")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<FcmTokenResponse> deleteFcmToken(Authentication authentication) {
        return ApiResponse.ok(fcmDeviceTokenService.deleteToken(currentUser.requireUserId(authentication)));
    }
}
