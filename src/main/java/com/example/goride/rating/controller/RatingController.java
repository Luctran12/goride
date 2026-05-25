package com.example.goride.rating.controller;

import com.example.goride.common.api.ApiResponse;
import com.example.goride.common.security.CurrentUser;
import com.example.goride.rating.dto.RatingCreateRequest;
import com.example.goride.rating.dto.RatingResponse;
import com.example.goride.rating.service.RatingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ratings")
public class RatingController {
    private final RatingService ratingService;
    private final CurrentUser currentUser;

    public RatingController(RatingService ratingService, CurrentUser currentUser) {
        this.ratingService = ratingService;
        this.currentUser = currentUser;
    }

    @PostMapping
    @PreAuthorize("hasRole('PASSENGER')")
    public ResponseEntity<ApiResponse<RatingResponse>> createRating(
            Authentication authentication,
            @Valid @RequestBody RatingCreateRequest request
    ) {
        RatingResponse response = ratingService.createRating(
                currentUser.requireUserId(authentication),
                request
        );
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(response));
    }
}
