package com.example.goride.booking.controller;

import com.example.goride.booking.dto.PricingConfigResponse;
import com.example.goride.booking.service.PricingConfigService;
import com.example.goride.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pricing")
public class PricingController {
    private final PricingConfigService pricingConfigService;

    public PricingController(PricingConfigService pricingConfigService) {
        this.pricingConfigService = pricingConfigService;
    }

    @GetMapping
    public ApiResponse<List<PricingConfigResponse>> listActivePricing() {
        return ApiResponse.ok(pricingConfigService.listActivePricing());
    }
}
