package com.example.goride.payment.service;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.booking.domain.Trip;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.payment.domain.Payment;
import com.example.goride.payment.domain.PaymentStatus;
import com.example.goride.payment.provider.CashPaymentProvider;
import com.example.goride.payment.provider.PaymentProvider;
import com.example.goride.payment.provider.PaymentProviderRegistry;
import com.example.goride.payment.provider.PaymentWebhookRequest;
import com.example.goride.payment.provider.PaymentWebhookResult;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentWebhookServiceTests {
    private static final Instant FIXED_NOW = Instant.parse("2026-06-13T06:00:00Z");

    @Test
    void dispatchesWebhookToProviderByProviderName() {
        CapturingWebhookProvider provider = new CapturingWebhookProvider();
        PaymentWebhookService service = new PaymentWebhookService(
                new PaymentProviderRegistry(List.of(provider)),
                fixedClock()
        );

        var response = service.handleProviderWebhook(
                " MoMo ",
                Map.of("x-signature", "signed"),
                Map.of("orderId", "txn-123")
        );

        assertThat(response.provider()).isEqualTo("momo");
        assertThat(response.accepted()).isTrue();
        assertThat(response.paymentId()).isEqualTo(70L);
        assertThat(response.tripId()).isEqualTo(99L);
        assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(response.transactionRef()).isEqualTo("txn-123");
        assertThat(provider.request.providerName()).isEqualTo("momo");
        assertThat(provider.request.headers()).containsEntry("x-signature", "signed");
        assertThat(provider.request.payload()).containsEntry("orderId", "txn-123");
        assertThat(provider.request.receivedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void rejectsUnsupportedProviderName() {
        PaymentWebhookService service = new PaymentWebhookService(
                new PaymentProviderRegistry(List.of()),
                fixedClock()
        );

        assertThatThrownBy(() -> service.handleProviderWebhook("momo", Map.of(), Map.of()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.PAYMENT_PROVIDER_UNSUPPORTED)
                );
    }

    @Test
    void rejectsProviderWithoutWebhookSupport() {
        PaymentWebhookService service = new PaymentWebhookService(
                new PaymentProviderRegistry(List.of(new CashPaymentProvider())),
                fixedClock()
        );

        assertThatThrownBy(() -> service.handleProviderWebhook("cash", Map.of(), Map.of()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.PAYMENT_PROVIDER_UNSUPPORTED)
                );
    }

    private Clock fixedClock() {
        return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    }

    private static final class CapturingWebhookProvider implements PaymentProvider {
        private PaymentWebhookRequest request;

        @Override
        public PaymentMethod paymentMethod() {
            return PaymentMethod.CASH;
        }

        @Override
        public String providerName() {
            return "momo";
        }

        @Override
        public Payment createPendingPayment(Trip trip) {
            return Payment.createPending(trip);
        }

        @Override
        public PaymentWebhookResult handleWebhook(PaymentWebhookRequest request) {
            this.request = request;
            return new PaymentWebhookResult(
                    true,
                    70L,
                    99L,
                    PaymentStatus.COMPLETED,
                    "txn-123",
                    "processed"
            );
        }
    }
}
