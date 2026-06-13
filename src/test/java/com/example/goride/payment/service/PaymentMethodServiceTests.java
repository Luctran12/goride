package com.example.goride.payment.service;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.payment.config.PaymentProviderProperties;
import com.example.goride.payment.dto.PaymentMethodResponse;
import com.example.goride.payment.provider.CashPaymentProvider;
import com.example.goride.payment.provider.MoMoPaymentClient;
import com.example.goride.payment.provider.MoMoPaymentProvider;
import com.example.goride.payment.provider.PaymentProviderRegistry;
import com.example.goride.payment.provider.VnPayPaymentProvider;
import com.example.goride.payment.repository.PaymentRepository;
import com.example.goride.payment.service.PaymentCompletionWorkflow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PaymentMethodServiceTests {

    @Test
    void listsCashEnabledAndOnlineProvidersDisabledByDefault() {
        PaymentMethodService service = new PaymentMethodService(
                new PaymentProviderRegistry(List.of(new CashPaymentProvider())),
                new PaymentProviderProperties()
        );

        List<PaymentMethodResponse> methods = service.listPaymentMethods();

        assertThat(methods).extracting(PaymentMethodResponse::method)
                .containsExactly(PaymentMethod.CASH, PaymentMethod.MOMO, PaymentMethod.VNPAY);
        PaymentMethodResponse cash = find(methods, PaymentMethod.CASH);
        assertThat(cash.enabled()).isTrue();
        assertThat(cash.checkoutRequired()).isFalse();
        assertThat(cash.providerConfigured()).isTrue();
        assertThat(cash.providerRegistered()).isTrue();

        PaymentMethodResponse momo = find(methods, PaymentMethod.MOMO);
        assertThat(momo.enabled()).isFalse();
        assertThat(momo.checkoutRequired()).isTrue();
        assertThat(momo.sandbox()).isTrue();
        assertThat(momo.providerConfigured()).isFalse();
        assertThat(momo.providerRegistered()).isFalse();
    }

    @Test
    void enablesOnlineMethodOnlyWhenProviderIsRegisteredAndConfigured() {
        PaymentProviderProperties properties = new PaymentProviderProperties();
        PaymentProviderProperties.ProviderSettings momo = properties.getMomo();
        momo.setEnabled(true);
        momo.setMerchantId("merchant");
        momo.setAccessKey("access-key");
        momo.setSecretKey("secret");
        momo.setCheckoutBaseUrl("https://sandbox.momo.example/checkout");
        momo.setReturnUrl("https://api.goride.example/payments/momo/return");
        momo.setIpnUrl("https://api.goride.example/payments/momo/ipn");

        PaymentMethodService withoutProvider = new PaymentMethodService(
                new PaymentProviderRegistry(List.of(new CashPaymentProvider())),
                properties
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
                                mock(PaymentCompletionWorkflow.class)
                        )
                )),
                properties
        );
        PaymentMethodResponse momoResponse = find(withProvider.listPaymentMethods(), PaymentMethod.MOMO);

        assertThat(withProvider.isPaymentMethodEnabled(PaymentMethod.MOMO)).isTrue();
        assertThat(momoResponse.enabled()).isTrue();
        assertThat(momoResponse.providerConfigured()).isTrue();
        assertThat(momoResponse.providerRegistered()).isTrue();
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
                                mock(PaymentCompletionWorkflow.class)
                        )
                )),
                properties
        );

        PaymentMethodResponse vnpayResponse = find(service.listPaymentMethods(), PaymentMethod.VNPAY);

        assertThat(vnpayResponse.enabled()).isTrue();
        assertThat(vnpayResponse.providerConfigured()).isTrue();
        assertThat(vnpayResponse.providerRegistered()).isTrue();
    }

    private PaymentMethodResponse find(List<PaymentMethodResponse> methods, PaymentMethod method) {
        return methods.stream()
                .filter(response -> response.method() == method)
                .findFirst()
                .orElseThrow();
    }
}
