package com.example.goride.servicearea.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.time.Instant;

@Entity
@Table(
        name = "service_areas",
        indexes = {
                @Index(name = "idx_service_areas_city_active", columnList = "city_name, is_active")
        }
)
public class ServiceArea {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "city_name", nullable = false, length = 120)
    private String cityName;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode = "VN";

    @Column(nullable = false, columnDefinition = "geometry(Polygon,4326)")
    private Polygon boundary;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ServiceArea() {
    }

    public static ServiceArea create(
            String name,
            String cityName,
            String countryCode,
            Polygon boundary,
            Boolean active
    ) {
        ServiceArea area = new ServiceArea();
        area.name = requireText(name, "name");
        area.cityName = requireText(cityName, "cityName");
        area.countryCode = normalizeCountryCode(countryCode);
        area.boundary = requireBoundary(boundary);
        area.active = active == null || active;
        return area;
    }

    public void update(
            String name,
            String cityName,
            String countryCode,
            Polygon boundary,
            Boolean active
    ) {
        if (name != null) {
            this.name = requireText(name, "name");
        }
        if (cityName != null) {
            this.cityName = requireText(cityName, "cityName");
        }
        if (countryCode != null) {
            this.countryCode = normalizeCountryCode(countryCode);
        }
        if (boundary != null) {
            this.boundary = requireBoundary(boundary);
        }
        if (active != null) {
            this.active = active;
        }
    }

    public void deactivate() {
        this.active = false;
    }

    public boolean covers(Point point) {
        return active && boundary != null && boundary.covers(point);
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

    public String getName() {
        return name;
    }

    public String getCityName() {
        return cityName;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public Polygon getBoundary() {
        return boundary;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private static Polygon requireBoundary(Polygon boundary) {
        if (boundary == null || boundary.isEmpty()) {
            throw new IllegalArgumentException("boundary must not be empty");
        }
        if (!boundary.isValid()) {
            throw new IllegalArgumentException("boundary must be a valid polygon");
        }
        if (boundary.getSRID() != 4326) {
            throw new IllegalArgumentException("boundary SRID must be 4326");
        }
        return boundary;
    }

    private static String normalizeCountryCode(String countryCode) {
        String normalized = countryCode == null || countryCode.isBlank() ? "VN" : countryCode.trim().toUpperCase();
        if (normalized.length() != 2 || !normalized.matches("[A-Z]{2}")) {
            throw new IllegalArgumentException("countryCode must be a 2-letter ISO code");
        }
        return normalized;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}