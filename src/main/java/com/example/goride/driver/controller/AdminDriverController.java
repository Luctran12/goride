package com.example.goride.driver.controller;

import com.example.goride.common.api.ApiResponse;
import com.example.goride.common.api.PageResponse;
import com.example.goride.driver.dto.DriverApprovalUpdateRequest;
import com.example.goride.driver.dto.DriverProfileResponse;
import com.example.goride.driver.service.DriverApprovalService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/admin/drivers")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDriverController {
    private final DriverApprovalService driverApprovalService;

    public AdminDriverController(DriverApprovalService driverApprovalService) {
        this.driverApprovalService = driverApprovalService;
    }

    @GetMapping("/pending")
    public ApiResponse<PageResponse<DriverProfileResponse>> listPendingDrivers(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.ok(driverApprovalService.listPendingDrivers(page, size));
    }

    @PatchMapping("/{driverId}/approval")
    public ApiResponse<DriverProfileResponse> updateDriverApproval(
            @PathVariable Long driverId,
            @Valid @RequestBody DriverApprovalUpdateRequest request
    ) {
        return ApiResponse.ok(driverApprovalService.updateApproval(driverId, request));
    }
}
