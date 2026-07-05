package com.example.goride.payment.dto;

import com.example.goride.payment.domain.PaymentSandboxUatStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record PaymentSandboxUatResultRequest(
        @NotNull
        PaymentSandboxUatStatus status,

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
