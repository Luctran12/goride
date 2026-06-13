package com.example.goride.payment.provider;

public record MoMoCreatePaymentResponse(
        String partnerCode,
        String requestId,
        String orderId,
        Long amount,
        Long responseTime,
        String message,
        Integer resultCode,
        String payUrl,
        String deeplink,
        String qrCodeUrl,
        String signature
) {
}
