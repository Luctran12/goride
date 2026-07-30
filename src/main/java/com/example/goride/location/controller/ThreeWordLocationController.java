package com.example.goride.location.controller;

import com.example.goride.common.api.ApiResponse;
import com.example.goride.location.dto.ThreeWordLocationResponse;
import com.example.goride.location.service.ThreeWordLocationService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@Validated
@RestController
@RequestMapping("/api/v1/locations")
public class ThreeWordLocationController {
    private final ThreeWordLocationService threeWordLocationService;

    public ThreeWordLocationController(ThreeWordLocationService threeWordLocationService) {
        this.threeWordLocationService = threeWordLocationService;
    }

    @GetMapping("/to-words")
    @PreAuthorize("hasAnyRole('PASSENGER', 'DRIVER')")
    public ApiResponse<ThreeWordLocationResponse> toWords(
            @RequestParam
            @DecimalMin("-90.0")
            @DecimalMax("90.0")
            BigDecimal lat,

            @RequestParam
            @DecimalMin("-180.0")
            @DecimalMax("180.0")
            BigDecimal lng
    ) {
        return ApiResponse.ok(threeWordLocationService.toWords(lat, lng));
    }

    @GetMapping("/to-coordinate")
    @PreAuthorize("hasAnyRole('PASSENGER', 'DRIVER')")
    public ApiResponse<ThreeWordLocationResponse> toCoordinate(
            @RequestParam
            @NotBlank
            @Size(max = 200)
            String address
    ) {
        return ApiResponse.ok(threeWordLocationService.toCoordinate(address));
    }
}
