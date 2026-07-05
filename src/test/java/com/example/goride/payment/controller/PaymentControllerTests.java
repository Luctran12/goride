package com.example.goride.payment.controller;

import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.common.security.CurrentUser;
import com.example.goride.payment.domain.PaymentSandboxUatStatus;
import com.example.goride.payment.domain.PaymentStatus;
import com.example.goride.payment.dto.PaymentProviderReadinessResponse;
import com.example.goride.payment.dto.PaymentSandboxUatPlanResponse;
import com.example.goride.payment.dto.PaymentSandboxUatResultRequest;
import com.example.goride.payment.dto.PaymentSandboxUatResultResponse;
import com.example.goride.payment.dto.PaymentWebhookResponse;
import com.example.goride.payment.dto.VnPayIpnResponse;
import com.example.goride.payment.service.PaymentCheckoutService;
import com.example.goride.payment.service.PaymentMethodService;
import com.example.goride.payment.service.PaymentProviderReadinessService;
import com.example.goride.payment.service.PaymentQueryService;
import com.example.goride.payment.service.PaymentSandboxUatPlanService;
import com.example.goride.payment.service.PaymentSandboxUatResultService;
import com.example.goride.payment.service.PaymentWebhookService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentControllerTests {

    @Test
    void returnsProviderReadinessForAdminDiagnostics() {
        PaymentProviderReadinessService readinessService = mock(PaymentProviderReadinessService.class);
        PaymentProviderReadinessResponse readiness = mock(PaymentProviderReadinessResponse.class);
        when(readinessService.listProviderReadiness()).thenReturn(List.of(readiness));
        PaymentController controller = controller(mock(PaymentWebhookService.class), readinessService);

        var response = controller.listPaymentProviderReadiness();

        assertThat(response.data()).containsExactly(readiness);
    }

    @Test
    void returnsSandboxUatPlanForAdminDiagnostics() {
        PaymentSandboxUatPlanService uatPlanService = mock(PaymentSandboxUatPlanService.class);
        PaymentSandboxUatPlanResponse plan = new PaymentSandboxUatPlanResponse(
                List.of("Configure sandbox credentials"),
                List.of(),
                List.of("Run success callback")
        );
        when(uatPlanService.getSandboxUatPlan()).thenReturn(plan);
        PaymentController controller = controller(
                mock(PaymentWebhookService.class),
                mock(PaymentProviderReadinessService.class),
                uatPlanService,
                mock(PaymentSandboxUatResultService.class),
                mock(CurrentUser.class)
        );

        var response = controller.getPaymentSandboxUatPlan();

        assertThat(response.data()).isSameAs(plan);
    }

    @Test
    void returnsSandboxUatResultsForAdminDiagnostics() {
        PaymentSandboxUatResultService resultService = mock(PaymentSandboxUatResultService.class);
        PaymentSandboxUatResultResponse result = mock(PaymentSandboxUatResultResponse.class);
        when(resultService.listResults()).thenReturn(List.of(result));
        PaymentController controller = controller(
                mock(PaymentWebhookService.class),
                mock(PaymentProviderReadinessService.class),
                mock(PaymentSandboxUatPlanService.class),
                resultService,
                mock(CurrentUser.class)
        );

        var response = controller.listPaymentSandboxUatResults();

        assertThat(response.data()).containsExactly(result);
    }

    @Test
    void updatesSandboxUatResultWithCurrentAdminUser() {
        PaymentSandboxUatResultService resultService = mock(PaymentSandboxUatResultService.class);
        PaymentSandboxUatResultRequest request = new PaymentSandboxUatResultRequest(
                PaymentSandboxUatStatus.PASSED,
                true,
                true,
                true,
                true,
                true,
                "Passed sandbox UAT",
                Instant.parse("2026-07-05T10:00:00Z")
        );
        PaymentSandboxUatResultResponse result = mock(PaymentSandboxUatResultResponse.class);
        when(resultService.upsertResult("momo", request, 42L)).thenReturn(result);
        CurrentUser currentUser = mock(CurrentUser.class);
        Authentication authentication = mock(Authentication.class);
        when(currentUser.requireUserId(authentication)).thenReturn(42L);
        PaymentController controller = controller(
                mock(PaymentWebhookService.class),
                mock(PaymentProviderReadinessService.class),
                mock(PaymentSandboxUatPlanService.class),
                resultService,
                currentUser
        );

        var response = controller.updatePaymentSandboxUatResult(authentication, "momo", request);

        assertThat(response.data()).isSameAs(result);
        verify(resultService).upsertResult("momo", request, 42L);
    }

    @Test
    void returnsNoContentForHandledMomoIpn() {
        PaymentWebhookService paymentWebhookService = mock(PaymentWebhookService.class);
        when(paymentWebhookService.handleProviderWebhook(eq("momo"), anyMap(), anyMap()))
                .thenReturn(new PaymentWebhookResponse(
                        "momo",
                        true,
                        70L,
                        99L,
                        PaymentStatus.COMPLETED,
                        "4088878653",
                        "MoMo payment completed"
                ));
        PaymentController controller = controller(paymentWebhookService);

        Object response = controller.handleProviderWebhook(
                "momo",
                Map.of("content-type", "application/json"),
                Map.of("orderId", "GORIDE-PAY-70")
        );

        assertThat(response).isInstanceOf(ResponseEntity.class);
        assertThat(((ResponseEntity<?>) response).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(((ResponseEntity<?>) response).getBody()).isNull();
    }

    @Test
    void returnsVnPayConfirmSuccessResponseForHandledIpn() {
        PaymentWebhookService paymentWebhookService = mock(PaymentWebhookService.class);
        when(paymentWebhookService.handleProviderWebhook(eq("vnpay"), anyMap(), anyMap()))
                .thenReturn(new PaymentWebhookResponse(
                        "vnpay",
                        true,
                        70L,
                        99L,
                        PaymentStatus.COMPLETED,
                        "14123456",
                        "VNPay payment completed"
                ));
        PaymentController controller = controller(paymentWebhookService);

        Object response = controller.handleProviderWebhookQuery(
                "vnpay",
                Map.of(),
                Map.of("vnp_TxnRef", "GORIDE-PAY-70")
        );

        assertThat(response).isEqualTo(VnPayIpnResponse.confirmSuccess());
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(paymentWebhookService).handleProviderWebhook(
                eq("vnpay"),
                eq(Map.of()),
                payloadCaptor.capture()
        );
        assertThat(payloadCaptor.getValue()).containsEntry("vnp_TxnRef", "GORIDE-PAY-70");
    }

    @Test
    void returnsVnPayInvalidSignatureResponse() {
        PaymentWebhookService paymentWebhookService = mock(PaymentWebhookService.class);
        when(paymentWebhookService.handleProviderWebhook(eq("vnpay"), anyMap(), anyMap()))
                .thenThrow(new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "Invalid VNPay secure hash"
                ));
        PaymentController controller = controller(paymentWebhookService);

        Object response = controller.handleProviderWebhookQuery(
                "vnpay",
                Map.of(),
                Map.of("vnp_SecureHash", "invalid")
        );

        assertThat(response).isEqualTo(new VnPayIpnResponse("97", "Invalid signature"));
    }

    private PaymentController controller(PaymentWebhookService paymentWebhookService) {
        return controller(
                paymentWebhookService,
                mock(PaymentProviderReadinessService.class),
                mock(PaymentSandboxUatPlanService.class),
                mock(PaymentSandboxUatResultService.class),
                mock(CurrentUser.class)
        );
    }

    private PaymentController controller(
            PaymentWebhookService paymentWebhookService,
            PaymentProviderReadinessService readinessService
    ) {
        return controller(
                paymentWebhookService,
                readinessService,
                mock(PaymentSandboxUatPlanService.class),
                mock(PaymentSandboxUatResultService.class),
                mock(CurrentUser.class)
        );
    }

    private PaymentController controller(
            PaymentWebhookService paymentWebhookService,
            PaymentProviderReadinessService readinessService,
            PaymentSandboxUatPlanService uatPlanService,
            PaymentSandboxUatResultService resultService,
            CurrentUser currentUser
    ) {
        return new PaymentController(
                mock(PaymentQueryService.class),
                mock(PaymentCheckoutService.class),
                mock(PaymentMethodService.class),
                readinessService,
                uatPlanService,
                resultService,
                paymentWebhookService,
                currentUser
        );
    }
}
