package com.example.goride.payment.service;

import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.payment.domain.Payment;
import com.example.goride.payment.domain.PaymentStatus;
import com.example.goride.payment.dto.PaymentCheckoutResponse;
import com.example.goride.payment.provider.PaymentCheckoutSession;
import com.example.goride.payment.provider.PaymentProviderRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentCheckoutService {
    private final PaymentAccessService paymentAccessService;
    private final PaymentProviderRegistry paymentProviderRegistry;

    public PaymentCheckoutService(
            PaymentAccessService paymentAccessService,
            PaymentProviderRegistry paymentProviderRegistry
    ) {
        this.paymentAccessService = paymentAccessService;
        this.paymentProviderRegistry = paymentProviderRegistry;
    }

    @Transactional(readOnly = true)
    public PaymentCheckoutResponse getTripPaymentCheckout(Long currentUserId, Long tripId) {
        Payment payment = paymentAccessService.requireTripPayment(currentUserId, tripId);
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_INVALID_STATUS,
                    "Checkout is only available for pending payment"
            );
        }

        PaymentCheckoutSession checkoutSession = paymentProviderRegistry
                .requireProvider(payment.getMethod())
                .createCheckoutSession(payment);
        return PaymentCheckoutResponse.from(payment, checkoutSession);
    }
}
