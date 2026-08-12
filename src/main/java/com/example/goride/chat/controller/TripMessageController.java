package com.example.goride.chat.controller;

import com.example.goride.chat.dto.TripMessageCreateRequest;
import com.example.goride.chat.dto.TripMessageResponse;
import com.example.goride.chat.dto.TripMessageReadRequest;
import com.example.goride.chat.dto.TripMessageReadStateResponse;
import com.example.goride.chat.dto.TripMessageSyncResponse;
import com.example.goride.chat.dto.TripMessageUnreadCountResponse;
import com.example.goride.chat.service.TripMessageReadService;
import com.example.goride.chat.service.TripMessageService;
import com.example.goride.common.api.ApiResponse;
import com.example.goride.common.api.PageResponse;
import com.example.goride.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trips/{tripId}/messages")
public class TripMessageController {
    private final TripMessageService tripMessageService;
    private final TripMessageReadService tripMessageReadService;
    private final CurrentUser currentUser;

    public TripMessageController(
            TripMessageService tripMessageService,
            TripMessageReadService tripMessageReadService,
            CurrentUser currentUser
    ) {
        this.tripMessageService = tripMessageService;
        this.tripMessageReadService = tripMessageReadService;
        this.currentUser = currentUser;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PASSENGER', 'DRIVER', 'ADMIN')")
    public ApiResponse<PageResponse<TripMessageResponse>> listMessages(
            Authentication authentication,
            @PathVariable Long tripId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return ApiResponse.ok(tripMessageService.listMessages(
                currentUser.requireUserId(authentication),
                hasRole(authentication, "ROLE_ADMIN"),
                tripId,
                page,
                size
        ));
    }

    @PutMapping("/read-state")
    @PreAuthorize("hasAnyRole('PASSENGER', 'DRIVER')")
    public ApiResponse<TripMessageReadStateResponse> markRead(
            Authentication authentication,
            @PathVariable Long tripId,
            @Valid @RequestBody TripMessageReadRequest request
    ) {
        return ApiResponse.ok(tripMessageReadService.markRead(
                currentUser.requireUserId(authentication),
                tripId,
                request
        ));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("hasAnyRole('PASSENGER', 'DRIVER')")
    public ApiResponse<TripMessageUnreadCountResponse> unreadCount(
            Authentication authentication,
            @PathVariable Long tripId
    ) {
        return ApiResponse.ok(tripMessageReadService.unreadCount(
                currentUser.requireUserId(authentication),
                tripId
        ));
    }

    @GetMapping("/sync")
    @PreAuthorize("hasAnyRole('PASSENGER', 'DRIVER', 'ADMIN')")
    public ApiResponse<TripMessageSyncResponse> syncMessages(
            Authentication authentication,
            @PathVariable Long tripId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Long afterId,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ApiResponse.ok(tripMessageService.syncMessages(
                currentUser.requireUserId(authentication),
                hasRole(authentication, "ROLE_ADMIN"),
                tripId,
                beforeId,
                afterId,
                limit
        ));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PASSENGER', 'DRIVER')")
    public ResponseEntity<ApiResponse<TripMessageResponse>> sendMessage(
            Authentication authentication,
            @PathVariable Long tripId,
            @Valid @RequestBody TripMessageCreateRequest request
    ) {
        TripMessageResponse response = tripMessageService.sendMessage(
                currentUser.requireUserId(authentication),
                tripId,
                request
        );
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(response));
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role::equals);
    }
}
