package com.example.goride.payment.provider;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.booking.domain.Trip;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.payment.domain.Payment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentProviderRegistryTests {

    @Test
    void returnsRegisteredProviderByPaymentMethod() {
        PaymentProvider provider = new CashPaymentProvider();
        PaymentProviderRegistry registry = new PaymentProviderRegistry(List.of(provider));

        assertThat(registry.requireProvider(PaymentMethod.CASH)).isSameAs(provider);
    }

    @Test
    void returnsRegisteredProviderByProviderName() {
        PaymentProvider provider = providerNamed("MoMo");
        PaymentProviderRegistry registry = new PaymentProviderRegistry(List.of(provider));

        assertThat(registry.requireProvider(" momo ")).isSameAs(provider);
    }

    @Test
    void rejectsPaymentMethodWithoutRegisteredProvider() {
        PaymentProviderRegistry registry = new PaymentProviderRegistry(List.of());

        assertThatThrownBy(() -> registry.requireProvider(PaymentMethod.CASH))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.PAYMENT_INVALID_STATUS)
                );
    }

    @Test
    void rejectsUnsupportedProviderName() {
        PaymentProviderRegistry registry = new PaymentProviderRegistry(List.of());

        assertThatThrownBy(() -> registry.requireProvider("momo"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.PAYMENT_PROVIDER_UNSUPPORTED)
                );
    }

    @Test
    void rejectsDuplicateProviderForSamePaymentMethod() {
        assertThatThrownBy(() -> new PaymentProviderRegistry(List.of(
                        new CashPaymentProvider(),
                        new CashPaymentProvider()
                )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate payment provider");
    }

    private PaymentProvider providerNamed(String providerName) {
        return new PaymentProvider() {
            @Override
            public PaymentMethod paymentMethod() {
                return PaymentMethod.CASH;
            }

            @Override
            public String providerName() {
                return providerName;
            }

            @Override
            public Payment createPendingPayment(Trip trip) {
                return Payment.createPending(trip);
            }
        };
    }
}
