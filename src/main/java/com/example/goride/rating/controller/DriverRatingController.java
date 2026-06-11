package com.example.goride.rating.controller;

import com.example.goride.common.api.ApiResponse;
import com.example.goride.common.api.PageResponse;
import com.example.goride.rating.dto.RatingResponse;
import com.example.goride.rating.service.RatingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/drivers")
public class DriverRatingController {
    private final RatingService ratingService;

    public DriverRatingController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    @GetMapping("/{driverId}/ratings")
    public ApiResponse<PageResponse<RatingResponse>> listDriverRatings(
            @PathVariable Long driverId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(ratingService.listDriverRatings(driverId, page, size));
    }
}
