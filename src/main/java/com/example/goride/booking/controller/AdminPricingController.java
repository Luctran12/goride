package com.example.goride.booking.controller;

import com.example.goride.booking.dto.FareSurgeResponse;
import com.example.goride.booking.dto.PricingConfigCreateRequest;
import com.example.goride.booking.dto.PricingConfigResponse;
import com.example.goride.booking.dto.SurgePricingRuleCreateRequest;
import com.example.goride.booking.dto.SurgePricingRuleResponse;
import com.example.goride.booking.dto.SurgePricingRuleUpdateRequest;
import com.example.goride.booking.service.PricingConfigService;
import com.example.goride.booking.service.SurgePricingService;
import com.example.goride.common.api.ApiResponse;
import com.example.goride.driver.domain.VehicleType;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/pricing")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPricingController {
    private final PricingConfigService pricingConfigService;
    private final SurgePricingService surgePricingService;

    public AdminPricingController(
            PricingConfigService pricingConfigService,
            SurgePricingService surgePricingService
    ) {
        this.pricingConfigService = pricingConfigService;
        this.surgePricingService = surgePricingService;
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

    @GetMapping("/surge-rules")
    public ApiResponse<List<SurgePricingRuleResponse>> listSurgeRules() {
        return ApiResponse.ok(surgePricingService.listRules());
    }

    @GetMapping("/surge-status")
    public ApiResponse<FareSurgeResponse> currentSurgeStatus(@RequestParam VehicleType vehicleType) {
        return ApiResponse.ok(surgePricingService.currentStatus(vehicleType));
    }

    @PostMapping("/surge-rules")
    public ResponseEntity<ApiResponse<SurgePricingRuleResponse>> createSurgeRule(
            @Valid @RequestBody SurgePricingRuleCreateRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(surgePricingService.createRule(request)));
    }

    @PatchMapping("/surge-rules/{ruleId}")
    public ApiResponse<SurgePricingRuleResponse> updateSurgeRule(
            @PathVariable Long ruleId,
            @Valid @RequestBody SurgePricingRuleUpdateRequest request
    ) {
        return ApiResponse.ok(surgePricingService.updateRule(ruleId, request));
    }

    @PatchMapping("/surge-rules/{ruleId}/deactivate")
    public ApiResponse<SurgePricingRuleResponse> deactivateSurgeRule(@PathVariable Long ruleId) {
        return ApiResponse.ok(surgePricingService.deactivateRule(ruleId));
    }
}
