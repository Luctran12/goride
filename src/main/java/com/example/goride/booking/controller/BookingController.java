package com.example.goride.booking.controller;

import com.example.goride.booking.dto.BookingCancelRequest;
import com.example.goride.booking.dto.BookingCreateRequest;
import com.example.goride.booking.dto.BookingEstimateRequest;
import com.example.goride.booking.dto.FareEstimateResponse;
import com.example.goride.booking.dto.TripResponse;
import com.example.goride.booking.service.BookingService;
import com.example.goride.common.api.ApiResponse;
import com.example.goride.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {
    private final BookingService bookingService;
    private final CurrentUser currentUser;

    public BookingController(BookingService bookingService, CurrentUser currentUser) {
        this.bookingService = bookingService;
        this.currentUser = currentUser;
    }

    @PostMapping("/estimate")
    @PreAuthorize("hasRole('PASSENGER')")
    public ApiResponse<FareEstimateResponse> estimateFare(@Valid @RequestBody BookingEstimateRequest request) {
        return ApiResponse.ok(bookingService.estimateFare(request));
    }

    @PostMapping
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<ApiResponse<TripResponse>> createBooking(
            Authentication authentication,
            @Valid @RequestBody BookingCreateRequest request
    ) {
        TripResponse response = bookingService.createBooking(currentUser.requireUserId(authentication), request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(response));
    }

    @GetMapping("/{tripId}")
    @PreAuthorize("hasAnyRole('PASSENGER', 'DRIVER', 'ADMIN')")
    public ApiResponse<TripResponse> getMyBooking(Authentication authentication, @PathVariable Long tripId) {
        return ApiResponse.ok(bookingService.getMyBooking(currentUser.requireUserId(authentication), tripId));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PASSENGER', 'DRIVER')")
    public ApiResponse<List<TripResponse>> listMyBookings(Authentication authentication) {
        return ApiResponse.ok(bookingService.listMyBookings(currentUser.requireUserId(authentication)));
    }

    @PatchMapping("/{tripId}/cancel")
    @PreAuthorize("hasAnyRole('PASSENGER', 'DRIVER', 'ADMIN')")
    public ApiResponse<TripResponse> cancelBooking(
            Authentication authentication,
            @PathVariable Long tripId,
            @Valid @RequestBody BookingCancelRequest request
    ) {
        return ApiResponse.ok(bookingService.cancelBooking(
                currentUser.requireUserId(authentication),
                tripId,
                request
        ));
    }
}
