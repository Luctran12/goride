package com.example.goride.payment.controller;

import com.example.goride.common.api.ApiResponse;
import com.example.goride.common.security.CurrentUser;
import com.example.goride.payment.dto.PaymentDetailResponse;
import com.example.goride.payment.service.PaymentQueryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentQueryService paymentQueryService;
    private final CurrentUser currentUser;

    public PaymentController(PaymentQueryService paymentQueryService, CurrentUser currentUser) {
        this.paymentQueryService = paymentQueryService;
        this.currentUser = currentUser;
    }

    @GetMapping("/trips/{tripId}")
    @PreAuthorize("hasAnyRole('PASSENGER', 'DRIVER', 'ADMIN')")
    public ApiResponse<PaymentDetailResponse> getTripPayment(
            Authentication authentication,
            @PathVariable Long tripId
    ) {
        return ApiResponse.ok(paymentQueryService.getTripPayment(
                currentUser.requireUserId(authentication),
                tripId
        ));
    }
}
