package com.example.goride.driver.controller;

import com.example.goride.common.api.ApiResponse;
import com.example.goride.common.security.CurrentUser;
import com.example.goride.driver.dto.DriverProfileResponse;
import com.example.goride.driver.dto.DriverStatusUpdateRequest;
import com.example.goride.driver.service.DriverProfileService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/drivers/me/status")
public class DriverStatusController {
    private final DriverProfileService driverProfileService;
    private final CurrentUser currentUser;

    public DriverStatusController(DriverProfileService driverProfileService, CurrentUser currentUser) {
        this.driverProfileService = driverProfileService;
        this.currentUser = currentUser;
    }

    @PatchMapping
    @PreAuthorize("hasRole('DRIVER')")
    public ApiResponse<DriverProfileResponse> updateMyStatus(
            Authentication authentication,
            @Valid @RequestBody DriverStatusUpdateRequest request
    ) {
        return ApiResponse.ok(driverProfileService.updateMyStatus(
                currentUser.requireUserId(authentication),
                request
        ));
    }
}
