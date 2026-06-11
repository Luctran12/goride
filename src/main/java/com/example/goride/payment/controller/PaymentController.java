package com.example.goride.payment.controller;

import com.example.goride.common.api.ApiResponse;
import com.example.goride.common.security.CurrentUser;
import com.example.goride.payment.dto.PaymentCheckoutResponse;
import com.example.goride.payment.dto.PaymentDetailResponse;
import com.example.goride.payment.dto.PaymentWebhookResponse;
import com.example.goride.payment.service.PaymentCheckoutService;
import com.example.goride.payment.service.PaymentQueryService;
import com.example.goride.payment.service.PaymentWebhookService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentQueryService paymentQueryService;
    private final PaymentCheckoutService paymentCheckoutService;
    private final PaymentWebhookService paymentWebhookService;
    private final CurrentUser currentUser;

    public PaymentController(
            PaymentQueryService paymentQueryService,
            PaymentCheckoutService paymentCheckoutService,
            PaymentWebhookService paymentWebhookService,
            CurrentUser currentUser
    ) {
        this.paymentQueryService = paymentQueryService;
        this.paymentCheckoutService = paymentCheckoutService;
        this.paymentWebhookService = paymentWebhookService;
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

    @GetMapping("/trips/{tripId}/checkout")
    @PreAuthorize("hasAnyRole('PASSENGER', 'DRIVER', 'ADMIN')")
    public ApiResponse<PaymentCheckoutResponse> getTripPaymentCheckout(
            Authentication authentication,
            @PathVariable Long tripId
    ) {
        return ApiResponse.ok(paymentCheckoutService.getTripPaymentCheckout(
                currentUser.requireUserId(authentication),
                tripId
        ));
    }

    @PostMapping("/providers/{providerName}/webhook")
    public ApiResponse<PaymentWebhookResponse> handleProviderWebhook(
            @PathVariable String providerName,
            @RequestHeader Map<String, String> headers,
            @RequestBody(required = false) Map<String, Object> payload
    ) {
        return ApiResponse.ok(paymentWebhookService.handleProviderWebhook(
                providerName,
                headers,
                payload
        ));
    }
}
