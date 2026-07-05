package com.example.goride.payment.dto;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.payment.domain.PaymentSandboxUatResult;
import com.example.goride.payment.domain.PaymentSandboxUatStatus;

import java.time.Instant;
import java.util.List;

public record PaymentSandboxUatResultResponse(
        PaymentMethod method,
        String provider,
        String displayName,
        boolean sandboxReady,
        boolean readyForFrontendExposure,
        PaymentSandboxUatStatus status,
        boolean checkoutUrlTested,
        boolean successCallbackTested,
        boolean failureCallbackTested,
        boolean idempotentReplayTested,
        boolean freshnessRejectionTested,
        List<String> missingChecks,
        String notes,
        Instant testedAt,
        Long testedByUserId,
        Instant updatedAt
) {
    public static PaymentSandboxUatResultResponse from(
            PaymentProviderReadinessResponse readiness,
            PaymentSandboxUatResult result
    ) {
        PaymentSandboxUatStatus status = result == null
                ? PaymentSandboxUatStatus.NOT_RUN
                : result.getStatus();
        boolean checkoutUrlTested = result != null && result.isCheckoutUrlTested();
        boolean successCallbackTested = result != null && result.isSuccessCallbackTested();
        boolean failureCallbackTested = result != null && result.isFailureCallbackTested();
        boolean idempotentReplayTested = result != null && result.isIdempotentReplayTested();
        boolean freshnessRejectionTested = result != null && result.isFreshnessRejectionTested();
        boolean evidencePassed = result != null && result.passedAllRequiredChecks();
        return new PaymentSandboxUatResultResponse(
                readiness.method(),
                readiness.provider(),
                readiness.displayName(),
                readiness.sandboxReady(),
                readiness.sandboxReady() && evidencePassed,
                status,
                checkoutUrlTested,
                successCallbackTested,
                failureCallbackTested,
                idempotentReplayTested,
                freshnessRejectionTested,
                missingChecks(
                        checkoutUrlTested,
                        successCallbackTested,
                        failureCallbackTested,
                        idempotentReplayTested,
                        freshnessRejectionTested
                ),
                result == null ? null : result.getNotes(),
                result == null ? null : result.getTestedAt(),
                result == null ? null : result.getTestedByUserId(),
                result == null ? null : result.getUpdatedAt()
        );
    }

    private static List<String> missingChecks(
            boolean checkoutUrlTested,
            boolean successCallbackTested,
            boolean failureCallbackTested,
            boolean idempotentReplayTested,
            boolean freshnessRejectionTested
    ) {
        java.util.ArrayList<String> missing = new java.util.ArrayList<>();
        if (!checkoutUrlTested) {
            missing.add("checkout-url");
        }
        if (!successCallbackTested) {
            missing.add("success-callback");
        }
        if (!failureCallbackTested) {
            missing.add("failure-callback");
        }
        if (!idempotentReplayTested) {
            missing.add("idempotent-replay");
        }
        if (!freshnessRejectionTested) {
            missing.add("freshness-rejection");
        }
        return List.copyOf(missing);
    }
}
