package com.example.goride.chat.domain;

import com.example.goride.booking.domain.Trip;
import com.example.goride.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "trip_messages",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_trip_messages_trip_sender_client",
                columnNames = {"trip_id", "sender_id", "client_message_id"}
        ),
        indexes = {
                @Index(name = "idx_trip_messages_trip_sent_at", columnList = "trip_id, sent_at"),
                @Index(name = "idx_trip_messages_sender_sent_at", columnList = "sender_id, sent_at")
        }
)
public class TripMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_role", nullable = false, length = 20)
    private TripMessageSenderRole senderRole;

    @Column(name = "client_message_id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID clientMessageId;

    @Column(nullable = false, length = 1000)
    private String body;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    protected TripMessage() {
    }

    public static TripMessage create(
            Trip trip,
            User sender,
            TripMessageSenderRole senderRole,
            UUID clientMessageId,
            String body
    ) {
        TripMessage message = new TripMessage();
        message.trip = requireNonNull(trip, "trip");
        message.sender = requireNonNull(sender, "sender");
        message.senderRole = requireNonNull(senderRole, "senderRole");
        message.clientMessageId = requireNonNull(clientMessageId, "clientMessageId");
        message.body = requireBody(body);
        message.sentAt = Instant.now();
        return message;
    }

    @PrePersist
    void prePersist() {
        if (sentAt == null) {
            sentAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Trip getTrip() {
        return trip;
    }

    public User getSender() {
        return sender;
    }

    public TripMessageSenderRole getSenderRole() {
        return senderRole;
    }

    public UUID getClientMessageId() {
        return clientMessageId;
    }

    public String getBody() {
        return body;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    private static String requireBody(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("body must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > 1000) {
            throw new IllegalArgumentException("body must not exceed 1000 characters");
        }
        return normalized;
    }

    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }
}
