package com.example.goride.payment.service;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.payment.config.PaymentProviderProperties;
import com.example.goride.payment.domain.PaymentSandboxUatResult;
import com.example.goride.payment.domain.PaymentSandboxUatStatus;
import com.example.goride.payment.dto.PaymentMethodResponse;
import com.example.goride.payment.provider.CashPaymentProvider;
import com.example.goride.payment.provider.MoMoPaymentClient;
import com.example.goride.payment.provider.MoMoPaymentProvider;
import com.example.goride.payment.provider.PaymentProviderRegistry;
import com.example.goride.payment.provider.PaymentWebhookFreshnessPolicy;
import com.example.goride.payment.provider.VnPayPaymentProvider;
import com.example.goride.payment.repository.PaymentRepository;
import com.example.goride.payment.repository.PaymentSandboxUatResultRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentMethodServiceTests {

    @Test
    void listsCashEnabledAndOnlineProvidersDisabledByDefault() {
        PaymentMethodService service = new PaymentMethodService(
                new PaymentProviderRegistry(List.of(new CashPaymentProvider())),
                new PaymentProviderProperties(),
                mock(PaymentSandboxUatResultRepository.class)
        );

        List<PaymentMethodResponse> methods = service.listPaymentMethods();

        assertThat(methods).extracting(PaymentMethodResponse::method)
                .containsExactly(PaymentMethod.CASH, PaymentMethod.MOMO, PaymentMethod.VNPAY);
        PaymentMethodResponse cash = find(methods, PaymentMethod.CASH);
        assertThat(cash.enabled()).isTrue();
        assertThat(cash.consumerEnabled()).isTrue();
        assertThat(cash.readyForFrontendExposure()).isTrue();
        assertThat(cash.checkoutRequired()).isFalse();
        assertThat(cash.providerConfigured()).isTrue();
        assertThat(cash.providerRegistered()).isTrue();

        PaymentMethodResponse momo = find(methods, PaymentMethod.MOMO);
        assertThat(momo.enabled()).isFalse();
        assertThat(momo.consumerEnabled()).isFalse();
        assertThat(momo.readyForFrontendExposure()).isFalse();
        assertThat(momo.checkoutRequired()).isTrue();
        assertThat(momo.sandbox()).isTrue();
        assertThat(momo.providerConfigured()).isFalse();
        assertThat(momo.providerRegistered()).isFalse();
    }

    @Test
    void keepsOnlineMethodForUatButBlocksConsumerExposureUntilSandboxEvidencePasses() {
        PaymentProviderProperties properties = momoProperties();

        PaymentMethodService withoutProvider = new PaymentMethodService(
                new PaymentProviderRegistry(List.of(new CashPaymentProvider())),
                properties,
                mock(PaymentSandboxUatResultRepository.class)
        );
        assertThat(withoutProvider.isPaymentMethodEnabled(PaymentMethod.MOMO)).isFalse();
        assertThat(find(withoutProvider.listPaymentMethods(), PaymentMethod.MOMO).providerConfigured()).isTrue();
        assertThat(find(withoutProvider.listPaymentMethods(), PaymentMethod.MOMO).providerRegistered()).isFalse();

        PaymentMethodService withProvider = new PaymentMethodService(
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
                properties,
                mock(PaymentSandboxUatResultRepository.class)
        );
        PaymentMethodResponse momoResponse = find(withProvider.listPaymentMethods(), PaymentMethod.MOMO);

        assertThat(withProvider.isPaymentMethodEnabled(PaymentMethod.MOMO)).isTrue();
        assertThat(momoResponse.enabled()).isTrue();
        assertThat(momoResponse.providerConfigured()).isTrue();
        assertThat(momoResponse.providerRegistered()).isTrue();
        assertThat(momoResponse.readyForFrontendExposure()).isFalse();
        assertThat(momoResponse.consumerEnabled()).isFalse();
    }

    @Test
    void marksOnlineMethodConsumerEnabledAfterSandboxUatPasses() {
        PaymentProviderProperties properties = momoProperties();
        PaymentSandboxUatResultRepository repository = mock(PaymentSandboxUatResultRepository.class);
        when(repository.findByProviderName("momo")).thenReturn(Optional.of(passedSandboxUatResult("momo")));

        PaymentMethodService service = new PaymentMethodService(
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
                properties,
                repository
        );

        PaymentMethodResponse momoResponse = find(service.listPaymentMethods(), PaymentMethod.MOMO);

        assertThat(momoResponse.enabled()).isTrue();
        assertThat(momoResponse.readyForFrontendExposure()).isTrue();
        assertThat(momoResponse.consumerEnabled()).isTrue();
    }

    @Test
    void marksVnpayEnabledWhenCheckoutProviderAndConfigExist() {
        PaymentProviderProperties properties = new PaymentProviderProperties();
        PaymentProviderProperties.ProviderSettings vnpay = properties.getVnpay();
        vnpay.setEnabled(true);
        vnpay.setMerchantId("tmn-code");
        vnpay.setSecretKey("hash-secret");
        vnpay.setCheckoutBaseUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        vnpay.setReturnUrl("https://api.goride.example/payments/vnpay/return");

        PaymentMethodService service = new PaymentMethodService(
                new PaymentProviderRegistry(List.of(
                        new CashPaymentProvider(),
                        new VnPayPaymentProvider(
                                properties,
                                java.time.Clock.systemUTC(),
                                mock(PaymentRepository.class),
                                mock(PaymentCompletionWorkflow.class),
                                new PaymentWebhookFreshnessPolicy()
                        )
                )),
                properties,
                mock(PaymentSandboxUatResultRepository.class)
        );

        PaymentMethodResponse vnpayResponse = find(service.listPaymentMethods(), PaymentMethod.VNPAY);

        assertThat(vnpayResponse.enabled()).isTrue();
        assertThat(vnpayResponse.providerConfigured()).isTrue();
        assertThat(vnpayResponse.providerRegistered()).isTrue();
        assertThat(vnpayResponse.readyForFrontendExposure()).isFalse();
        assertThat(vnpayResponse.consumerEnabled()).isFalse();
    }

    private PaymentProviderProperties momoProperties() {
        PaymentProviderProperties properties = new PaymentProviderProperties();
        PaymentProviderProperties.ProviderSettings momo = properties.getMomo();
        momo.setEnabled(true);
        momo.setMerchantId("merchant");
        momo.setAccessKey("access-key");
        momo.setSecretKey("secret");
        momo.setCheckoutBaseUrl("https://sandbox.momo.example/checkout");
        momo.setReturnUrl("https://api.goride.example/payments/momo/return");
        momo.setIpnUrl("https://api.goride.example/payments/momo/ipn");
        return properties;
    }

    private PaymentSandboxUatResult passedSandboxUatResult(String providerName) {
        PaymentSandboxUatResult result = PaymentSandboxUatResult.create(providerName);
        result.update(
                PaymentSandboxUatStatus.PASSED,
                true,
                true,
                true,
                true,
                true,
                "Sandbox callbacks passed",
                Instant.parse("2026-07-18T10:00:00Z"),
                99L
        );
        return result;
    }

    private PaymentMethodResponse find(List<PaymentMethodResponse> methods, PaymentMethod method) {
        return methods.stream()
                .filter(response -> response.method() == method)
                .findFirst()
                .orElseThrow();
    }
}
