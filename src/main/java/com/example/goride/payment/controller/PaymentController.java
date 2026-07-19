package com.example.goride.payment.controller;

import com.example.goride.common.api.ApiResponse;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.security.CurrentUser;
import com.example.goride.payment.dto.PaymentCheckoutResponse;
import com.example.goride.payment.dto.PaymentDetailResponse;
import com.example.goride.payment.dto.PaymentMethodResponse;
import com.example.goride.payment.dto.PaymentProviderReadinessResponse;
import com.example.goride.payment.dto.PaymentSandboxE2eSessionRequest;
import com.example.goride.payment.dto.PaymentSandboxE2eSessionResponse;
import com.example.goride.payment.dto.PaymentSandboxUatPlanResponse;
import com.example.goride.payment.dto.PaymentSandboxUatResultRequest;
import com.example.goride.payment.dto.PaymentSandboxUatResultResponse;
import com.example.goride.payment.dto.PaymentWebhookResponse;
import com.example.goride.payment.dto.VnPayIpnResponse;
import com.example.goride.payment.service.PaymentCheckoutService;
import com.example.goride.payment.service.PaymentMethodService;
import com.example.goride.payment.service.PaymentProviderReadinessService;
import com.example.goride.payment.service.PaymentQueryService;
import com.example.goride.payment.service.PaymentSandboxE2eSessionService;
import com.example.goride.payment.service.PaymentSandboxUatPlanService;
import com.example.goride.payment.service.PaymentSandboxUatResultService;
import com.example.goride.payment.service.PaymentWebhookService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentQueryService paymentQueryService;
    private final PaymentCheckoutService paymentCheckoutService;
    private final PaymentMethodService paymentMethodService;
    private final PaymentProviderReadinessService paymentProviderReadinessService;
    private final PaymentSandboxUatPlanService paymentSandboxUatPlanService;
    private final PaymentSandboxE2eSessionService paymentSandboxE2eSessionService;
    private final PaymentSandboxUatResultService paymentSandboxUatResultService;
    private final PaymentWebhookService paymentWebhookService;
    private final CurrentUser currentUser;

    public PaymentController(
            PaymentQueryService paymentQueryService,
            PaymentCheckoutService paymentCheckoutService,
            PaymentMethodService paymentMethodService,
            PaymentProviderReadinessService paymentProviderReadinessService,
            PaymentSandboxUatPlanService paymentSandboxUatPlanService,
            PaymentSandboxE2eSessionService paymentSandboxE2eSessionService,
            PaymentSandboxUatResultService paymentSandboxUatResultService,
            PaymentWebhookService paymentWebhookService,
            CurrentUser currentUser
    ) {
        this.paymentQueryService = paymentQueryService;
        this.paymentCheckoutService = paymentCheckoutService;
        this.paymentMethodService = paymentMethodService;
        this.paymentProviderReadinessService = paymentProviderReadinessService;
        this.paymentSandboxUatPlanService = paymentSandboxUatPlanService;
        this.paymentSandboxE2eSessionService = paymentSandboxE2eSessionService;
        this.paymentSandboxUatResultService = paymentSandboxUatResultService;
        this.paymentWebhookService = paymentWebhookService;
        this.currentUser = currentUser;
    }

    @GetMapping("/methods")
    public ApiResponse<List<PaymentMethodResponse>> listPaymentMethods() {
        return ApiResponse.ok(paymentMethodService.listPaymentMethods());
    }

    @GetMapping("/providers/readiness")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<PaymentProviderReadinessResponse>> listPaymentProviderReadiness() {
        return ApiResponse.ok(paymentProviderReadinessService.listProviderReadiness());
    }

    @GetMapping("/providers/sandbox-uat-plan")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PaymentSandboxUatPlanResponse> getPaymentSandboxUatPlan() {
        return ApiResponse.ok(paymentSandboxUatPlanService.getSandboxUatPlan());
    }

    @GetMapping("/providers/sandbox-uat-results")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<PaymentSandboxUatResultResponse>> listPaymentSandboxUatResults() {
        return ApiResponse.ok(paymentSandboxUatResultService.listResults());
    }

    @GetMapping("/providers/sandbox-e2e-sessions")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<PaymentSandboxE2eSessionResponse>> listPaymentSandboxE2eSessions() {
        return ApiResponse.ok(paymentSandboxE2eSessionService.listSessions());
    }

    @GetMapping("/providers/{providerName}/sandbox-e2e-sessions")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<PaymentSandboxE2eSessionResponse>> listProviderPaymentSandboxE2eSessions(
            @PathVariable String providerName
    ) {
        return ApiResponse.ok(paymentSandboxE2eSessionService.listProviderSessions(providerName));
    }

    @PostMapping("/providers/{providerName}/sandbox-e2e-sessions")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PaymentSandboxE2eSessionResponse> recordProviderPaymentSandboxE2eSession(
            Authentication authentication,
            @PathVariable String providerName,
            @Valid @RequestBody PaymentSandboxE2eSessionRequest request
    ) {
        return ApiResponse.ok(paymentSandboxE2eSessionService.recordSession(
                providerName,
                request,
                currentUser.requireUserId(authentication)
        ));
    }

    @PutMapping("/providers/{providerName}/sandbox-uat-result")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PaymentSandboxUatResultResponse> updatePaymentSandboxUatResult(
            Authentication authentication,
            @PathVariable String providerName,
            @Valid @RequestBody PaymentSandboxUatResultRequest request
    ) {
        return ApiResponse.ok(paymentSandboxUatResultService.upsertResult(
                providerName,
                request,
                currentUser.requireUserId(authentication)
        ));
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
        return tripPaymentCheckout(authentication, tripId);
    }

    @PostMapping("/trips/{tripId}/checkout")
    @PreAuthorize("hasAnyRole('PASSENGER', 'DRIVER', 'ADMIN')")
    public ApiResponse<PaymentCheckoutResponse> createTripPaymentCheckout(
            Authentication authentication,
            @PathVariable Long tripId
    ) {
        return tripPaymentCheckout(authentication, tripId);
    }

    private ApiResponse<PaymentCheckoutResponse> tripPaymentCheckout(
            Authentication authentication,
            Long tripId
    ) {
        return ApiResponse.ok(paymentCheckoutService.getTripPaymentCheckout(
                currentUser.requireUserId(authentication),
                tripId
        ));
    }

    @PostMapping("/providers/{providerName}/webhook")
    public Object handleProviderWebhook(
            @PathVariable String providerName,
            @RequestHeader Map<String, String> headers,
            @RequestBody(required = false) Map<String, Object> payload
    ) {
        PaymentWebhookResponse response = paymentWebhookService.handleProviderWebhook(
                providerName,
                headers,
                payload
        );
        if ("momo".equalsIgnoreCase(providerName)) {
            return ResponseEntity.noContent().build();
        }
        return ApiResponse.ok(response);
    }

    @GetMapping("/providers/{providerName}/webhook")
    public Object handleProviderWebhookQuery(
            @PathVariable String providerName,
            @RequestHeader Map<String, String> headers,
            @RequestParam Map<String, String> queryParams
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.putAll(queryParams);
        if ("vnpay".equalsIgnoreCase(providerName)) {
            return handleVnPayIpn(providerName, headers, payload);
        }
        return ApiResponse.ok(paymentWebhookService.handleProviderWebhook(
                providerName,
                headers,
                payload
        ));
    }

    private VnPayIpnResponse handleVnPayIpn(
            String providerName,
            Map<String, String> headers,
            Map<String, Object> payload
    ) {
        try {
            paymentWebhookService.handleProviderWebhook(providerName, headers, payload);
            return VnPayIpnResponse.confirmSuccess();
        } catch (BusinessException exception) {
            log.warn("Rejected VNPay IPN callback: {}", exception.getMessage());
            return VnPayIpnResponse.from(exception);
        } catch (Exception exception) {
            log.error("Failed to process VNPay IPN callback", exception);
            return VnPayIpnResponse.unknownError();
        }
    }
}
