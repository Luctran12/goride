package com.example.goride.servicearea.controller;

import com.example.goride.common.api.ApiResponse;
import com.example.goride.servicearea.dto.ServiceAreaCreateRequest;
import com.example.goride.servicearea.dto.ServiceAreaResponse;
import com.example.goride.servicearea.dto.ServiceAreaUpdateRequest;
import com.example.goride.servicearea.service.ServiceAreaService;
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
@RequestMapping("/api/v1/admin/service-areas")
@PreAuthorize("hasRole('ADMIN')")
public class AdminServiceAreaController {
    private final ServiceAreaService serviceAreaService;

    public AdminServiceAreaController(ServiceAreaService serviceAreaService) {
        this.serviceAreaService = serviceAreaService;
    }

    @GetMapping
    public ApiResponse<List<ServiceAreaResponse>> listAllServiceAreas() {
        return ApiResponse.ok(serviceAreaService.listAll());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ServiceAreaResponse>> createServiceArea(
            @Valid @RequestBody ServiceAreaCreateRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(serviceAreaService.create(request)));
    }

    @PatchMapping("/{serviceAreaId}")
    public ApiResponse<ServiceAreaResponse> updateServiceArea(
            @PathVariable Long serviceAreaId,
            @Valid @RequestBody ServiceAreaUpdateRequest request
    ) {
        return ApiResponse.ok(serviceAreaService.update(serviceAreaId, request));
    }

    @PatchMapping("/{serviceAreaId}/deactivate")
    public ApiResponse<ServiceAreaResponse> deactivateServiceArea(@PathVariable Long serviceAreaId) {
        return ApiResponse.ok(serviceAreaService.deactivate(serviceAreaId));
    }
}