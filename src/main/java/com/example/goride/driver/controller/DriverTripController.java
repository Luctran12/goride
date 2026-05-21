package com.example.goride.driver.controller;

import com.example.goride.common.api.ApiResponse;
import com.example.goride.common.security.CurrentUser;
import com.example.goride.driver.dto.DriverTripRespondRequest;
import com.example.goride.driver.dto.DriverTripResponse;
import com.example.goride.matching.service.DriverOfferResponseService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/drivers/trips")
public class DriverTripController {
    private final DriverOfferResponseService driverOfferResponseService;
    private final CurrentUser currentUser;

    public DriverTripController(DriverOfferResponseService driverOfferResponseService, CurrentUser currentUser) {
        this.driverOfferResponseService = driverOfferResponseService;
        this.currentUser = currentUser;
    }

    @PatchMapping("/{tripId}/respond")
    @PreAuthorize("hasRole('DRIVER')")
    public ApiResponse<DriverTripResponse> respondToOffer(
            Authentication authentication,
            @PathVariable Long tripId,
            @Valid @RequestBody DriverTripRespondRequest request
    ) {
        DriverTripResponse response = driverOfferResponseService.respondToOffer(
                currentUser.requireUserId(authentication),
                tripId,
                request.action()
        );
        return ApiResponse.ok(response);
    }
}
