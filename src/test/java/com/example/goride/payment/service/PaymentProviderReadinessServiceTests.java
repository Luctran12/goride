package com.example.goride.payment.service;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.payment.config.PaymentProviderProperties;
import com.example.goride.payment.dto.PaymentProviderReadinessResponse;
import com.example.goride.payment.provider.CashPaymentProvider;
import com.example.goride.payment.provider.MoMoPaymentClient;
import com.example.goride.payment.provider.MoMoPaymentProvider;
import com.example.goride.payment.provider.PaymentProviderRegistry;
import com.example.goride.payment.provider.PaymentWebhookFreshnessPolicy;
import com.example.goride.payment.provider.VnPayPaymentProvider;
import com.example.goride.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PaymentProviderReadinessServiceTests {

    @Test
    void reportsOnlineProvidersNotReadyByDefault() {
        PaymentProviderReadinessService service = new PaymentProviderReadinessService(
                new PaymentProviderRegistry(List.of(new CashPaymentProvider())),
                new PaymentProviderProperties()
        );

        List<PaymentProviderReadinessResponse> readiness = service.listProviderReadiness();

        assertThat(readiness).extracting(PaymentProviderReadinessResponse::method)
                .containsExactly(PaymentMethod.MOMO, PaymentMethod.VNPAY);
        PaymentProviderReadinessResponse momo = find(readiness, PaymentMethod.MOMO);
        assertThat(momo.sandboxReady()).isFalse();
        assertThat(momo.checkoutReady()).isFalse();
        assertThat(momo.webhookReady()).isFalse();
        assertThat(momo.missingRequirements())
                .contains(
                        "provider-registered",
                        "provider-enabled",
                        "merchant-id",
                        "access-key",
                        "secret-key",
                        "checkout-base-url",
                        "return-url",
                        "ipn-url"
                );
    }

    @Test
    void marksMomoReadyWhenRegisteredEnabledAndConfigured() {
        PaymentProviderProperties properties = new PaymentProviderProperties();
        PaymentProviderProperties.ProviderSettings momo = properties.getMomo();
        momo.setEnabled(true);
        momo.setMerchantId("partner-code");
        momo.setAccessKey("access-key");
        momo.setSecretKey("secret-key");
        momo.setCheckoutBaseUrl("https://test-payment.momo.vn/v2/gateway/api/create");
        momo.setReturnUrl("https://api.goride.example/payments/momo/return");
        momo.setIpnUrl("https://api.goride.example/api/v1/payments/providers/momo/webhook");

        PaymentProviderReadinessService service = new PaymentProviderReadinessService(
                new PaymentProviderRegistry(List.of(
                        new CashPaymentProvider(),
                        new MoMoPaymentProvider(
                                properties,
                                mock(MoMoPaymentClient.class),
                                mock(PaymentRepository.class),
                                mock(PaymentCompletionWorkflow.class),
                                new PaymentWebhookFreshnessPolicy()
                        )
                )),
                properties
        );

        PaymentProviderReadinessResponse response = find(service.listProviderReadiness(), PaymentMethod.MOMO);

        assertThat(response.enabled()).isTrue();
        assertThat(response.providerRegistered()).isTrue();
        assertThat(response.checkoutConfigured()).isTrue();
        assertThat(response.webhookConfigured()).isTrue();
        assertThat(response.sandboxReady()).isTrue();
        assertThat(response.missingRequirements()).isEmpty();
    }

    @Test
    void keepsVnpaySandboxNotReadyUntilWebhookSecretIsConfigured() {
        PaymentProviderProperties properties = new PaymentProviderProperties();
        PaymentProviderProperties.ProviderSettings vnpay = properties.getVnpay();
        vnpay.setEnabled(true);
        vnpay.setMerchantId("tmn-code");
        vnpay.setSecretKey("checkout-secret");
        vnpay.setCheckoutBaseUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        vnpay.setReturnUrl("https://api.goride.example/payments/vnpay/return");

        PaymentProviderReadinessService service = new PaymentProviderReadinessService(
                new PaymentProviderRegistry(List.of(
                        new CashPaymentProvider(),
                        new VnPayPaymentProvider(
                                properties,
                                Clock.systemUTC(),
                                mock(PaymentRepository.class),
                                mock(PaymentCompletionWorkflow.class),
                                new PaymentWebhookFreshnessPolicy()
                        )
                )),
                properties
        );

        PaymentProviderReadinessResponse response = find(service.listProviderReadiness(), PaymentMethod.VNPAY);

        assertThat(response.checkoutReady()).isTrue();
        assertThat(response.webhookReady()).isFalse();
        assertThat(response.sandboxReady()).isFalse();
        assertThat(response.missingRequirements()).containsExactly("webhook-secret");
    }

    private PaymentProviderReadinessResponse find(
            List<PaymentProviderReadinessResponse> readiness,
            PaymentMethod paymentMethod
    ) {
        return readiness.stream()
                .filter(response -> response.method() == paymentMethod)
                .findFirst()
                .orElseThrow();
    }
}
