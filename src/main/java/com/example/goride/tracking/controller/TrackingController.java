package com.example.goride.tracking.controller;

import com.example.goride.common.api.ApiResponse;
import com.example.goride.common.security.CurrentUser;
import com.example.goride.tracking.dto.DriverLocationResponse;
import com.example.goride.tracking.service.TripLocationTrackingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tracking/trips")
public class TrackingController {
    private final TripLocationTrackingService tripLocationTrackingService;
    private final CurrentUser currentUser;

    public TrackingController(TripLocationTrackingService tripLocationTrackingService, CurrentUser currentUser) {
        this.tripLocationTrackingService = tripLocationTrackingService;
        this.currentUser = currentUser;
    }

    @GetMapping("/{tripId}/driver-location")
    @PreAuthorize("hasRole('PASSENGER')")
    public ApiResponse<DriverLocationResponse> getDriverLocation(
            Authentication authentication,
            @PathVariable Long tripId
    ) {
        return ApiResponse.ok(tripLocationTrackingService.getLatestDriverLocation(
                currentUser.requireUserId(authentication),
                tripId
        ));
    }
}
