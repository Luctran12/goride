package com.example.goride.payment.provider;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.booking.domain.Trip;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.payment.domain.Payment;

public interface PaymentProvider {
    PaymentMethod paymentMethod();

    default String providerName() {
        return paymentMethod().providerName();
    }

    Payment createPendingPayment(Trip trip);

    default PaymentCheckoutSession createCheckoutSession(Payment payment) {
        return PaymentCheckoutSession.notRequired();
    }

    default PaymentWebhookResult handleWebhook(PaymentWebhookRequest request) {
        throw new BusinessException(
                ErrorCode.PAYMENT_PROVIDER_UNSUPPORTED,
                "Payment provider does not support webhook callbacks: " + providerName()
        );
    }
}
