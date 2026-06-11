package com.example.goride.booking.controller;

import com.example.goride.booking.domain.TripStatus;
import com.example.goride.booking.dto.TripResponse;
import com.example.goride.booking.service.AdminTripService;
import com.example.goride.common.api.ApiResponse;
import com.example.goride.common.api.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@Validated
@RestController
@RequestMapping("/api/v1/admin/trips")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTripController {
    private final AdminTripService adminTripService;

    public AdminTripController(AdminTripService adminTripService) {
        this.adminTripService = adminTripService;
    }

    @GetMapping
    public ApiResponse<PageResponse<TripResponse>> listTrips(
            @RequestParam(required = false) TripStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.ok(adminTripService.listTrips(status, from, to, page, size));
    }
}
