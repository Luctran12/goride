package com.example.goride.tracking.domain;

import com.example.goride.booking.domain.Trip;
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
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "trip_location_history",
        indexes = {
                @Index(name = "idx_trip_location_trip", columnList = "trip_id, recorded_at")
        }
)
public class TripLocationHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(nullable = false, columnDefinition = "geometry(Point,4326)")
    private Point location;

    @Column(precision = 5, scale = 2)
    private BigDecimal bearing;

    @Column(precision = 6, scale = 2)
    private BigDecimal speed;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected TripLocationHistory() {
    }

    public static TripLocationHistory record(
            Trip trip,
            Point location,
            BigDecimal bearing,
            BigDecimal speed
    ) {
        TripLocationHistory history = new TripLocationHistory();
        history.trip = requireNonNull(trip, "trip");
        history.location = requireNonNull(location, "location");
        history.bearing = bearing;
        history.speed = speed;
        history.recordedAt = Instant.now();
        return history;
    }

    @PrePersist
    void prePersist() {
        if (recordedAt == null) {
            recordedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Trip getTrip() {
        return trip;
    }

    public Point getLocation() {
        return location;
    }

    public BigDecimal getBearing() {
        return bearing;
    }

    public BigDecimal getSpeed() {
        return speed;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }
}
