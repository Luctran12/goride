package com.example.goride.booking.controller;

import com.example.goride.booking.dto.PricingConfigCreateRequest;
import com.example.goride.booking.dto.PricingConfigResponse;
import com.example.goride.booking.service.PricingConfigService;
import com.example.goride.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/pricing")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPricingController {
    private final PricingConfigService pricingConfigService;

    public AdminPricingController(PricingConfigService pricingConfigService) {
        this.pricingConfigService = pricingConfigService;
    }

    @GetMapping
    public ApiResponse<List<PricingConfigResponse>> listAllPricing() {
        return ApiResponse.ok(pricingConfigService.listAllPricing());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PricingConfigResponse>> createPricing(
            @Valid @RequestBody PricingConfigCreateRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(pricingConfigService.createPricing(request)));
    }

    @PatchMapping("/{pricingConfigId}/deactivate")
    public ApiResponse<PricingConfigResponse> deactivatePricing(@PathVariable Long pricingConfigId) {
        return ApiResponse.ok(pricingConfigService.deactivatePricing(pricingConfigId));
    }
}
