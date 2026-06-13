package com.example.goride.payment.controller;

import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.common.security.CurrentUser;
import com.example.goride.payment.domain.PaymentStatus;
import com.example.goride.payment.dto.PaymentWebhookResponse;
import com.example.goride.payment.dto.VnPayIpnResponse;
import com.example.goride.payment.service.PaymentCheckoutService;
import com.example.goride.payment.service.PaymentMethodService;
import com.example.goride.payment.service.PaymentQueryService;
import com.example.goride.payment.service.PaymentWebhookService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentControllerTests {

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
        return new PaymentController(
                mock(PaymentQueryService.class),
                mock(PaymentCheckoutService.class),
                mock(PaymentMethodService.class),
                paymentWebhookService,
                mock(CurrentUser.class)
        );
    }
}
