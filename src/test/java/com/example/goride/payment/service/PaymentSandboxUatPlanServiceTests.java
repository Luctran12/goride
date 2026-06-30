package com.example.goride.payment.service;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.payment.config.PaymentProviderProperties;
import com.example.goride.payment.dto.PaymentProviderReadinessResponse;
import com.example.goride.payment.dto.PaymentSandboxUatPlanResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentSandboxUatPlanServiceTests {

    @Test
    void reportsDisabledProviderWithMissingRequirementsAndSafeEndpoints() {
        PaymentProviderProperties properties = new PaymentProviderProperties();
        PaymentProviderReadinessService readinessService = mock(PaymentProviderReadinessService.class);
        when(readinessService.listProviderReadiness()).thenReturn(List.of(new PaymentProviderReadinessResponse(
                PaymentMethod.MOMO,
                "momo",
                "MoMo",
                false,
                true,
                true,
                false,
                false,
                false,
                false,
                false,
                List.of("provider-enabled", "checkout-base-url", "ipn-url"),
                86_400,
                300
        )));
        PaymentSandboxUatPlanService service = new PaymentSandboxUatPlanService(readinessService, properties);

        PaymentSandboxUatPlanResponse response = service.getSandboxUatPlan();

        assertThat(response.prerequisites()).isNotEmpty();
        assertThat(response.validationScenarios()).isNotEmpty();
        PaymentSandboxUatPlanResponse.PaymentProviderSandboxUatResponse provider =
                response.providers().get(0);
        assertThat(provider.status()).isEqualTo("DISABLED");
        assertThat(provider.webhookEndpoint())
                .isEqualTo("POST /api/v1/payments/providers/momo/webhook");
        assertThat(provider.returnUrl()).isNull();
        assertThat(provider.ipnUrl()).isNull();
        assertThat(provider.missingRequirements())
                .containsExactly("provider-enabled", "checkout-base-url", "ipn-url");
        assertThat(provider.frontendActions())
                .contains("Hide or disable this online payment method outside admin/UAT screens.");
    }

    @Test
    void marksReadyProviderWithConfiguredCallbackUrlsAndUatActions() {
        PaymentProviderProperties properties = new PaymentProviderProperties();
        PaymentProviderProperties.ProviderSettings momo = properties.getMomo();
        momo.setReturnUrl("https://app.goride.test/payments/momo/return");
        momo.setIpnUrl("https://api.goride.test/api/v1/payments/providers/momo/webhook");
        PaymentProviderReadinessService readinessService = mock(PaymentProviderReadinessService.class);
        when(readinessService.listProviderReadiness()).thenReturn(List.of(new PaymentProviderReadinessResponse(
                PaymentMethod.MOMO,
                "momo",
                "MoMo",
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                List.of(),
                86_400,
                300
        )));
        PaymentSandboxUatPlanService service = new PaymentSandboxUatPlanService(readinessService, properties);

        PaymentSandboxUatPlanResponse.PaymentProviderSandboxUatResponse provider =
                service.getSandboxUatPlan().providers().get(0);

        assertThat(provider.status()).isEqualTo("READY_FOR_SANDBOX_UAT");
        assertThat(provider.checkoutEndpoint()).isEqualTo("GET /api/v1/payments/trips/{tripId}/checkout");
        assertThat(provider.returnUrl()).isEqualTo("https://app.goride.test/payments/momo/return");
        assertThat(provider.ipnUrl()).isEqualTo("https://api.goride.test/api/v1/payments/providers/momo/webhook");
        assertThat(provider.frontendActions())
                .contains("After provider redirect, call GET /api/v1/payments/trips/{tripId} until payment status is terminal.");
        assertThat(provider.backendChecks())
                .contains("Confirm duplicate terminal callbacks are accepted idempotently with the same transaction reference.");
    }

    @Test
    void flagsProviderWhenSandboxModeIsDisabled() {
        PaymentProviderProperties properties = new PaymentProviderProperties();
        PaymentProviderReadinessService readinessService = mock(PaymentProviderReadinessService.class);
        when(readinessService.listProviderReadiness()).thenReturn(List.of(new PaymentProviderReadinessResponse(
                PaymentMethod.VNPAY,
                "vnpay",
                "VNPay",
                true,
                false,
                true,
                true,
                true,
                true,
                true,
                false,
                List.of("sandbox-mode"),
                86_400,
                300
        )));
        PaymentSandboxUatPlanService service = new PaymentSandboxUatPlanService(readinessService, properties);

        PaymentSandboxUatPlanResponse.PaymentProviderSandboxUatResponse provider =
                service.getSandboxUatPlan().providers().get(0);

        assertThat(provider.status()).isEqualTo("SANDBOX_DISABLED");
        assertThat(provider.frontendActions())
                .contains("Show CASH fallback while missing requirements are resolved.");
    }
}
