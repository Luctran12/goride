package com.example.goride.payment.provider;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
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
    void rejectsPaymentMethodWithoutRegisteredProvider() {
        PaymentProviderRegistry registry = new PaymentProviderRegistry(List.of());

        assertThatThrownBy(() -> registry.requireProvider(PaymentMethod.CASH))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.PAYMENT_INVALID_STATUS)
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
}
