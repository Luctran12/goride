package com.example.goride.user.controller;

import com.example.goride.common.api.ApiResponse;
import com.example.goride.common.security.CurrentUser;
import com.example.goride.storage.domain.StoredFile;
import com.example.goride.storage.domain.UploadPurpose;
import com.example.goride.storage.service.FileStorageService;
import com.example.goride.user.dto.UserAvatarUploadResponse;
import com.example.goride.user.dto.UserPasswordChangeRequest;
import com.example.goride.user.dto.UserProfileUpdateRequest;
import com.example.goride.user.dto.UserResponse;
import com.example.goride.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users/me")
@PreAuthorize("isAuthenticated()")
public class UserProfileController {
    private final UserService userService;
    private final FileStorageService fileStorageService;
    private final CurrentUser currentUser;

    public UserProfileController(
            UserService userService,
            FileStorageService fileStorageService,
            CurrentUser currentUser
    ) {
        this.userService = userService;
        this.fileStorageService = fileStorageService;
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

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UserAvatarUploadResponse> uploadMyAvatar(
            Authentication authentication,
            @RequestPart("file") MultipartFile file
    ) {
        Long userId = currentUser.requireUserId(authentication);
        StoredFile storedFile = fileStorageService.store(file, UploadPurpose.USER_AVATAR, userId);
        userService.updateMyAvatar(userId, storedFile.url());
        return ApiResponse.ok(new UserAvatarUploadResponse(storedFile.url()));
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