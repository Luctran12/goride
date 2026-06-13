package com.example.goride.payment.provider;

public record MoMoCreatePaymentRequest(
        String partnerCode,
        String requestType,
        String ipnUrl,
        String redirectUrl,
        String orderId,
        long amount,
        String orderInfo,
        String requestId,
        String extraData,
        String signature,
        String lang
) {
}
