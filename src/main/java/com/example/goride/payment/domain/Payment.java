package com.example.goride.payment.domain;

import com.example.goride.booking.domain.PaymentMethod;
import com.example.goride.booking.domain.Trip;
import com.example.goride.booking.domain.TripStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "payments")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false, unique = true)
    private Trip trip;

    @Column(nullable = false, precision = 10, scale = 0)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method = PaymentMethod.CASH;

    @Column(length = 30)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "transaction_ref", length = 100)
    private String transactionRef;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() {
    }

    public static Payment createPending(Trip trip) {
        if (trip == null) {
            throw new IllegalArgumentException("trip must not be null");
        }
        if (trip.getStatus() != TripStatus.COMPLETED) {
            throw new IllegalArgumentException("Payment can only be created for completed trip");
        }
        if (trip.getFinalFare() == null) {
            throw new IllegalArgumentException("Trip final fare is required");
        }

        Payment payment = new Payment();
        payment.trip = trip;
        payment.amount = trip.getFinalFare();
        payment.method = trip.getPaymentMethod();
        payment.status = PaymentStatus.PENDING;
        return payment;
    }

    public void markCompleted() {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Payment can only be completed from pending status");
        }

        status = PaymentStatus.COMPLETED;
        paidAt = Instant.now();
    }

    public void markCompletedByProvider(String provider, String transactionRef) {
        String normalizedProvider = requireReferenceValue(provider, "provider", 30);
        String normalizedTransactionRef = requireReferenceValue(transactionRef, "transactionRef", 100);
        if (status == PaymentStatus.COMPLETED) {
            assertSameProviderReference(normalizedProvider, normalizedTransactionRef);
            return;
        }
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Payment can only be completed from pending status");
        }

        this.provider = normalizedProvider;
        this.transactionRef = normalizedTransactionRef;
        status = PaymentStatus.COMPLETED;
        paidAt = Instant.now();
    }

    public void markFailedByProvider(String provider, String transactionRef) {
        String normalizedProvider = requireReferenceValue(provider, "provider", 30);
        String normalizedTransactionRef = requireReferenceValue(transactionRef, "transactionRef", 100);
        if (status == PaymentStatus.FAILED) {
            assertSameProviderReference(normalizedProvider, normalizedTransactionRef);
            return;
        }
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Payment can only be failed from pending status");
        }

        this.provider = normalizedProvider;
        this.transactionRef = normalizedTransactionRef;
        status = PaymentStatus.FAILED;
    }

    private String requireReferenceValue(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        String normalizedValue = value.strip();
        if (normalizedValue.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must not exceed " + maxLength + " characters");
        }
        return normalizedValue;
    }

    private void assertSameProviderReference(String provider, String transactionRef) {
        if (!Objects.equals(this.provider, provider) || !Objects.equals(this.transactionRef, transactionRef)) {
            throw new IllegalStateException("Payment provider callback does not match existing provider reference");
        }
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

    public Trip getTrip() {
        return trip;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public String getProvider() {
        return provider;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getTransactionRef() {
        return transactionRef;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
