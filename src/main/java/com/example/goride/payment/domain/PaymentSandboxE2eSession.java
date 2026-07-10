package com.example.goride.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Locale;

@Entity
@Table(
        name = "payment_sandbox_e2e_sessions",
        indexes = {
                @Index(name = "idx_payment_sandbox_e2e_provider_tested_at", columnList = "provider_name, tested_at"),
                @Index(name = "idx_payment_sandbox_e2e_status", columnList = "status")
        }
)
public class PaymentSandboxE2eSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_name", nullable = false, length = 30)
    private String providerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentSandboxUatStatus status = PaymentSandboxUatStatus.NOT_RUN;

    @Column(name = "checkout_payment_id")
    private Long checkoutPaymentId;

    @Column(name = "checkout_url", length = 2048)
    private String checkoutUrl;

    @Column(name = "success_payment_id")
    private Long successPaymentId;

    @Column(name = "success_transaction_ref", length = 100)
    private String successTransactionRef;

    @Column(name = "failure_payment_id")
    private Long failurePaymentId;

    @Column(name = "failure_transaction_ref", length = 100)
    private String failureTransactionRef;

    @Column(name = "replay_transaction_ref", length = 100)
    private String replayTransactionRef;

    @Column(name = "checkout_url_tested", nullable = false)
    private boolean checkoutUrlTested;

    @Column(name = "success_callback_tested", nullable = false)
    private boolean successCallbackTested;

    @Column(name = "failure_callback_tested", nullable = false)
    private boolean failureCallbackTested;

    @Column(name = "idempotent_replay_tested", nullable = false)
    private boolean idempotentReplayTested;

    @Column(name = "freshness_rejection_tested", nullable = false)
    private boolean freshnessRejectionTested;

    @Column(length = 1000)
    private String notes;

    @Column(name = "tested_at")
    private Instant testedAt;

    @Column(name = "tested_by_user_id")
    private Long testedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentSandboxE2eSession() {
    }

    public static PaymentSandboxE2eSession create(String providerName) {
        PaymentSandboxE2eSession session = new PaymentSandboxE2eSession();
        session.providerName = normalizeProviderName(providerName);
        return session;
    }

    public void update(
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
            String notes,
            Instant testedAt,
            Long testedByUserId
    ) {
        this.status = status == null ? PaymentSandboxUatStatus.NOT_RUN : status;
        this.checkoutPaymentId = checkoutPaymentId;
        this.checkoutUrl = normalizeOptional(checkoutUrl, 2048, "checkoutUrl");
        this.successPaymentId = successPaymentId;
        this.successTransactionRef = normalizeOptional(successTransactionRef, 100, "successTransactionRef");
        this.failurePaymentId = failurePaymentId;
        this.failureTransactionRef = normalizeOptional(failureTransactionRef, 100, "failureTransactionRef");
        this.replayTransactionRef = normalizeOptional(replayTransactionRef, 100, "replayTransactionRef");
        this.checkoutUrlTested = checkoutUrlTested;
        this.successCallbackTested = successCallbackTested;
        this.failureCallbackTested = failureCallbackTested;
        this.idempotentReplayTested = idempotentReplayTested;
        this.freshnessRejectionTested = freshnessRejectionTested;
        this.notes = normalizeOptional(notes, 1000, "notes");
        this.testedAt = testedAt;
        this.testedByUserId = testedByUserId;
    }

    public boolean hasAllRequiredChecks() {
        return checkoutUrlTested
                && successCallbackTested
                && failureCallbackTested
                && idempotentReplayTested
                && freshnessRejectionTested;
    }

    public boolean passedAllRequiredChecks() {
        return status == PaymentSandboxUatStatus.PASSED && hasAllRequiredChecks();
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getProviderName() {
        return providerName;
    }

    public PaymentSandboxUatStatus getStatus() {
        return status;
    }

    public Long getCheckoutPaymentId() {
        return checkoutPaymentId;
    }

    public String getCheckoutUrl() {
        return checkoutUrl;
    }

    public Long getSuccessPaymentId() {
        return successPaymentId;
    }

    public String getSuccessTransactionRef() {
        return successTransactionRef;
    }

    public Long getFailurePaymentId() {
        return failurePaymentId;
    }

    public String getFailureTransactionRef() {
        return failureTransactionRef;
    }

    public String getReplayTransactionRef() {
        return replayTransactionRef;
    }

    public boolean isCheckoutUrlTested() {
        return checkoutUrlTested;
    }

    public boolean isSuccessCallbackTested() {
        return successCallbackTested;
    }

    public boolean isFailureCallbackTested() {
        return failureCallbackTested;
    }

    public boolean isIdempotentReplayTested() {
        return idempotentReplayTested;
    }

    public boolean isFreshnessRejectionTested() {
        return freshnessRejectionTested;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getTestedAt() {
        return testedAt;
    }

    public Long getTestedByUserId() {
        return testedByUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public static String normalizeProviderName(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            throw new IllegalArgumentException("providerName must not be blank");
        }
        return providerName.strip().toLowerCase(Locale.ROOT);
    }

    private static String normalizeOptional(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalizedValue = value.strip();
        if (normalizedValue.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must not exceed " + maxLength + " characters");
        }
        return normalizedValue;
    }
}