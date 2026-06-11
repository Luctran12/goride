package com.example.goride.payment.service;

import com.example.goride.payment.domain.Payment;
import com.example.goride.payment.dto.PaymentDetailResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentQueryService {
    private final PaymentAccessService paymentAccessService;

    public PaymentQueryService(PaymentAccessService paymentAccessService) {
        this.paymentAccessService = paymentAccessService;
    }

    @Transactional(readOnly = true)
    public PaymentDetailResponse getTripPayment(Long currentUserId, Long tripId) {
        Payment payment = paymentAccessService.requireTripPayment(currentUserId, tripId);
        return PaymentDetailResponse.from(payment);
    }
}
