package com.example.goride.payment.service;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.booking.domain.Trip;
import com.example.goride.payment.config.PaymentProviderProperties;
import com.example.goride.payment.domain.Payment;
import com.example.goride.payment.dto.PaymentMethodResponse;
import com.example.goride.payment.provider.CashPaymentProvider;
import com.example.goride.payment.provider.PaymentProvider;
import com.example.goride.payment.provider.PaymentProviderRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
        momo.setSecretKey("secret");
        momo.setCheckoutBaseUrl("https://sandbox.momo.example/checkout");

        PaymentMethodService withoutProvider = new PaymentMethodService(
                new PaymentProviderRegistry(List.of(new CashPaymentProvider())),
                properties
        );
        assertThat(withoutProvider.isPaymentMethodEnabled(PaymentMethod.MOMO)).isFalse();
        assertThat(find(withoutProvider.listPaymentMethods(), PaymentMethod.MOMO).providerConfigured()).isTrue();
        assertThat(find(withoutProvider.listPaymentMethods(), PaymentMethod.MOMO).providerRegistered()).isFalse();

        PaymentMethodService withProvider = new PaymentMethodService(
                new PaymentProviderRegistry(List.of(new CashPaymentProvider(), providerFor(PaymentMethod.MOMO))),
                properties
        );
        PaymentMethodResponse momoResponse = find(withProvider.listPaymentMethods(), PaymentMethod.MOMO);

        assertThat(withProvider.isPaymentMethodEnabled(PaymentMethod.MOMO)).isTrue();
        assertThat(momoResponse.enabled()).isTrue();
        assertThat(momoResponse.providerConfigured()).isTrue();
        assertThat(momoResponse.providerRegistered()).isTrue();
    }

    private PaymentMethodResponse find(List<PaymentMethodResponse> methods, PaymentMethod method) {
        return methods.stream()
                .filter(response -> response.method() == method)
                .findFirst()
                .orElseThrow();
    }

    private PaymentProvider providerFor(PaymentMethod paymentMethod) {
        return new PaymentProvider() {
            @Override
            public PaymentMethod paymentMethod() {
                return paymentMethod;
            }

            @Override
            public Payment createPendingPayment(Trip trip) {
                return Payment.createPending(trip);
            }
        };
    }
}
