package com.example.goride.user.controller;

import com.example.goride.common.api.ApiResponse;
import com.example.goride.common.security.CurrentUser;
import com.example.goride.user.dto.UserPasswordChangeRequest;
import com.example.goride.user.dto.UserProfileUpdateRequest;
import com.example.goride.user.dto.UserResponse;
import com.example.goride.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
@PreAuthorize("isAuthenticated()")
public class UserProfileController {
    private final UserService userService;
    private final CurrentUser currentUser;

    public UserProfileController(UserService userService, CurrentUser currentUser) {
        this.userService = userService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ApiResponse<UserResponse> getMyProfile(Authentication authentication) {
        return ApiResponse.ok(userService.getMyProfile(currentUser.requireUserId(authentication)));
    }

    @PutMapping
    public ApiResponse<UserResponse> updateMyProfile(
            Authentication authentication,
            @Valid @RequestBody UserProfileUpdateRequest request
    ) {
        return ApiResponse.ok(userService.updateMyProfile(currentUser.requireUserId(authentication), request));
    }

    @PutMapping("/password")
    public ApiResponse<Void> changeMyPassword(
            Authentication authentication,
            @Valid @RequestBody UserPasswordChangeRequest request
    ) {
        userService.changeMyPassword(currentUser.requireUserId(authentication), request);
        return ApiResponse.ok();
    }
}
