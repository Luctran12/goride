package com.example.goride.notification.controller;

import com.example.goride.common.api.ApiResponse;
import com.example.goride.common.api.PageResponse;
import com.example.goride.common.security.CurrentUser;
import com.example.goride.notification.dto.FcmTokenResponse;
import com.example.goride.notification.dto.FcmTokenUpdateRequest;
import com.example.goride.notification.dto.NotificationResponse;
import com.example.goride.notification.service.FcmDeviceTokenService;
import com.example.goride.notification.service.NotificationInboxService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

@Validated
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final FcmDeviceTokenService fcmDeviceTokenService;
    private final NotificationInboxService notificationInboxService;
    private final CurrentUser currentUser;

    public NotificationController(
            FcmDeviceTokenService fcmDeviceTokenService,
            NotificationInboxService notificationInboxService,
            CurrentUser currentUser
    ) {
        this.fcmDeviceTokenService = fcmDeviceTokenService;
        this.notificationInboxService = notificationInboxService;
        this.currentUser = currentUser;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PageResponse<NotificationResponse>> listMyNotifications(
            Authentication authentication,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.ok(notificationInboxService.listMyNotifications(
                currentUser.requireUserId(authentication),
                page,
                size
        ));
    }

    @PatchMapping("/{notificationId}/read")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<NotificationResponse> markRead(
            Authentication authentication,
            @PathVariable Long notificationId
    ) {
        return ApiResponse.ok(notificationInboxService.markRead(
                currentUser.requireUserId(authentication),
                notificationId
        ));
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
