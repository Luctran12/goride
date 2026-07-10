package com.example.goride.servicearea.controller;

import com.example.goride.common.api.ApiResponse;
import com.example.goride.servicearea.dto.ServiceAreaResponse;
import com.example.goride.servicearea.service.ServiceAreaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/service-areas")
public class ServiceAreaController {
    private final ServiceAreaService serviceAreaService;

    public ServiceAreaController(ServiceAreaService serviceAreaService) {
        this.serviceAreaService = serviceAreaService;
    }

    @GetMapping
    public ApiResponse<List<ServiceAreaResponse>> listActiveServiceAreas() {
        return ApiResponse.ok(serviceAreaService.listActive());
    }
}