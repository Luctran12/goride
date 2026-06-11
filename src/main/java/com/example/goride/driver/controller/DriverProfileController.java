package com.example.goride.driver.controller;

import com.example.goride.common.api.ApiResponse;
import com.example.goride.common.security.CurrentUser;
import com.example.goride.driver.dto.DriverProfileResponse;
import com.example.goride.driver.dto.DriverProfileUpsertRequest;
import com.example.goride.driver.service.DriverProfileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/drivers/me/profile")
public class DriverProfileController {
    private final DriverProfileService driverProfileService;
    private final CurrentUser currentUser;

    public DriverProfileController(DriverProfileService driverProfileService, CurrentUser currentUser) {
        this.driverProfileService = driverProfileService;
        this.currentUser = currentUser;
    }

    @GetMapping
    @PreAuthorize("hasRole('DRIVER')")
    public ApiResponse<DriverProfileResponse> getMyProfile(Authentication authentication) {
        return ApiResponse.ok(driverProfileService.getMyProfile(currentUser.requireUserId(authentication)));
    }

    @PostMapping
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<DriverProfileResponse>> createMyProfile(
            Authentication authentication,
            @Valid @RequestBody DriverProfileUpsertRequest request
    ) {
        DriverProfileResponse response = driverProfileService.createMyProfile(
                currentUser.requireUserId(authentication),
                request
        );
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(response));
    }
}
