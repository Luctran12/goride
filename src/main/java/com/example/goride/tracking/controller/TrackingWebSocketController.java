package com.example.goride.tracking.controller;

import com.example.goride.common.security.CurrentUser;
import com.example.goride.tracking.dto.DriverLocationUpdateRequest;
import com.example.goride.tracking.service.TripLocationTrackingService;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class TrackingWebSocketController {
    private final TripLocationTrackingService tripLocationTrackingService;
    private final CurrentUser currentUser;

    public TrackingWebSocketController(
            TripLocationTrackingService tripLocationTrackingService,
            CurrentUser currentUser
    ) {
        this.tripLocationTrackingService = tripLocationTrackingService;
        this.currentUser = currentUser;
    }

    @MessageMapping("/driver.location")
    @PreAuthorize("hasRole('DRIVER')")
    public void updateDriverLocation(
            Principal principal,
            @Valid @Payload DriverLocationUpdateRequest request
    ) {
        tripLocationTrackingService.updateDriverLocation(currentUser.requireUserId(principal), request);
    }
}
