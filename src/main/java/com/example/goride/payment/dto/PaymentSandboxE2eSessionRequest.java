package com.example.goride.payment.dto;

import com.example.goride.payment.domain.PaymentSandboxUatStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record PaymentSandboxE2eSessionRequest(
        @NotNull
        PaymentSandboxUatStatus status,

        @Positive
        Long checkoutPaymentId,

        @Size(max = 2048)
        String checkoutUrl,

        @Positive
        Long successPaymentId,

        @Size(max = 100)
        String successTransactionRef,

        @Positive
        Long failurePaymentId,

        @Size(max = 100)
        String failureTransactionRef,

        @Size(max = 100)
        String replayTransactionRef,

        boolean checkoutUrlTested,
        boolean successCallbackTested,
        boolean failureCallbackTested,
        boolean idempotentReplayTested,
        boolean freshnessRejectionTested,

        @Size(max = 1000)
        String notes,

        Instant testedAt
) {
}