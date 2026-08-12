package com.example.goride.chat.domain;

import com.example.goride.booking.domain.Trip;
import com.example.goride.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.util.Objects;

@Entity
@Table(
        name = "trip_message_read_states",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_trip_message_read_states_trip_user",
                columnNames = {"trip_id", "user_id"}
        ),
        indexes = @Index(
                name = "idx_trip_message_read_states_user_trip",
                columnList = "user_id, trip_id"
        )
)
public class TripMessageReadState {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "last_read_message_id", nullable = false)
    private TripMessage lastReadMessage;

    @Column(name = "read_at", nullable = false)
    private Instant readAt;

    protected TripMessageReadState() {
    }

    public static TripMessageReadState create(Trip trip, User user, TripMessage lastReadMessage) {
        TripMessageReadState state = new TripMessageReadState();
        state.trip = Objects.requireNonNull(trip, "trip");
        state.user = Objects.requireNonNull(user, "user");
        state.lastReadMessage = requireMessageFromTrip(trip, lastReadMessage);
        state.readAt = Instant.now();
        return state;
    }

    public void advanceTo(TripMessage message) {
        TripMessage candidate = requireMessageFromTrip(trip, message);
        if (candidate.getId() == null) {
            throw new IllegalArgumentException("lastReadMessage must be persisted");
        }
        if (lastReadMessage == null || lastReadMessage.getId() == null
                || candidate.getId() > lastReadMessage.getId()) {
            lastReadMessage = candidate;
            readAt = Instant.now();
        }
    }

    @PrePersist
    void prePersist() {
        if (readAt == null) {
            readAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Trip getTrip() {
        return trip;
    }

    public User getUser() {
        return user;
    }

    public TripMessage getLastReadMessage() {
        return lastReadMessage;
    }

    public Instant getReadAt() {
        return readAt;
    }

    private static TripMessage requireMessageFromTrip(Trip trip, TripMessage message) {
        TripMessage normalized = Objects.requireNonNull(message, "lastReadMessage");
        if (trip.getId() == null || normalized.getTrip().getId() == null
                || !Objects.equals(trip.getId(), normalized.getTrip().getId())) {
            throw new IllegalArgumentException("lastReadMessage must belong to trip");
        }
        return normalized;
    }
}
