package com.example.goride.payment.provider;

public interface MoMoPaymentClient {
    MoMoCreatePaymentResponse createPayment(
            String checkoutUrl,
            MoMoCreatePaymentRequest request
    );
}
