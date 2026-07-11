package com.example.goride.payment.dto;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.payment.domain.PaymentSandboxE2eSession;
import com.example.goride.payment.domain.PaymentSandboxUatStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public record PaymentSandboxE2eSessionResponse(
        Long id,
        PaymentMethod method,
        String provider,
        String displayName,
        boolean sandboxReady,
        boolean sessionEvidencePassed,
        PaymentSandboxUatStatus status,
        Long checkoutPaymentId,
        String checkoutUrl,
        Long successPaymentId,
        String successTransactionRef,
        Long failurePaymentId,
        String failureTransactionRef,
        String replayTransactionRef,
        boolean checkoutUrlTested,
        boolean successCallbackTested,
        boolean failureCallbackTested,
        boolean idempotentReplayTested,
        boolean freshnessRejectionTested,
        List<String> missingChecks,
        String notes,
        Instant testedAt,
        Long testedByUserId,
        Instant createdAt,
        Instant updatedAt
) {
    public static PaymentSandboxE2eSessionResponse from(
            PaymentProviderReadinessResponse readiness,
            PaymentSandboxE2eSession session
    ) {
        return new PaymentSandboxE2eSessionResponse(
                session.getId(),
                readiness.method(),
                readiness.provider(),
                readiness.displayName(),
                readiness.sandboxReady(),
                session.passedAllRequiredChecks(),
                session.getStatus(),
                session.getCheckoutPaymentId(),
                session.getCheckoutUrl(),
                session.getSuccessPaymentId(),
                session.getSuccessTransactionRef(),
                session.getFailurePaymentId(),
                session.getFailureTransactionRef(),
                session.getReplayTransactionRef(),
                session.isCheckoutUrlTested(),
                session.isSuccessCallbackTested(),
                session.isFailureCallbackTested(),
                session.isIdempotentReplayTested(),
                session.isFreshnessRejectionTested(),
                missingChecks(session),
                session.getNotes(),
                session.getTestedAt(),
                session.getTestedByUserId(),
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }

    private static List<String> missingChecks(PaymentSandboxE2eSession session) {
        ArrayList<String> missing = new ArrayList<>();
        if (!session.isCheckoutUrlTested()) {
            missing.add("checkout-url");
        }
        if (!session.isSuccessCallbackTested()) {
            missing.add("success-callback");
        }
        if (!session.isFailureCallbackTested()) {
            missing.add("failure-callback");
        }
        if (!session.isIdempotentReplayTested()) {
            missing.add("idempotent-replay");
        }
        if (!session.isFreshnessRejectionTested()) {
            missing.add("freshness-rejection");
        }
        return List.copyOf(missing);
    }
}