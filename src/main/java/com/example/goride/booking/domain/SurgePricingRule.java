package com.example.goride.booking.domain;

import com.example.goride.driver.domain.VehicleType;
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

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "surge_pricing_rules",
        indexes = {
                @Index(name = "idx_surge_pricing_rules_vehicle_active", columnList = "vehicle_type, is_active"),
                @Index(name = "idx_surge_pricing_rules_window", columnList = "starts_at, ends_at")
        }
)
public class SurgePricingRule {
    private static final BigDecimal MAX_MULTIPLIER = BigDecimal.valueOf(3);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private VehicleType vehicleType;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "min_demand_trips", nullable = false)
    private int minDemandTrips;

    @Column(name = "min_demand_supply_ratio", nullable = false, precision = 5, scale = 2)
    private BigDecimal minDemandSupplyRatio;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal multiplier;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SurgePricingRule() {
    }

    public static SurgePricingRule create(
            VehicleType vehicleType,
            String name,
            int minDemandTrips,
            BigDecimal minDemandSupplyRatio,
            BigDecimal multiplier,
            Boolean active,
            Instant startsAt,
            Instant endsAt
    ) {
        SurgePricingRule rule = new SurgePricingRule();
        rule.vehicleType = requireNonNull(vehicleType, "vehicleType");
        rule.name = requireText(name, "name");
        rule.minDemandTrips = requireAtLeastOne(minDemandTrips, "minDemandTrips");
        rule.minDemandSupplyRatio = requirePositive(minDemandSupplyRatio, "minDemandSupplyRatio");
        rule.multiplier = requireMultiplier(multiplier);
        rule.active = active == null || active;
        rule.startsAt = startsAt;
        rule.endsAt = endsAt;
        rule.validateWindow();
        return rule;
    }

    public void update(
            String name,
            Integer minDemandTrips,
            BigDecimal minDemandSupplyRatio,
            BigDecimal multiplier,
            Boolean active,
            Instant startsAt,
            Instant endsAt
    ) {
        if (name != null) {
            this.name = requireText(name, "name");
        }
        if (minDemandTrips != null) {
            this.minDemandTrips = requireAtLeastOne(minDemandTrips, "minDemandTrips");
        }
        if (minDemandSupplyRatio != null) {
            this.minDemandSupplyRatio = requirePositive(minDemandSupplyRatio, "minDemandSupplyRatio");
        }
        if (multiplier != null) {
            this.multiplier = requireMultiplier(multiplier);
        }
        if (active != null) {
            this.active = active;
        }
        if (startsAt != null) {
            this.startsAt = startsAt;
        }
        if (endsAt != null) {
            this.endsAt = endsAt;
        }
        validateWindow();
    }

    public void deactivate() {
        this.active = false;
    }

    public boolean isActiveAt(Instant instant) {
        if (!active) {
            return false;
        }
        Instant now = requireNonNull(instant, "instant");
        if (startsAt != null && now.isBefore(startsAt)) {
            return false;
        }
        return endsAt == null || now.isBefore(endsAt);
    }

    public boolean matches(long demandTrips, BigDecimal demandSupplyRatio, Instant instant) {
        return isActiveAt(instant)
                && demandTrips >= minDemandTrips
                && demandSupplyRatio.compareTo(minDemandSupplyRatio) >= 0;
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

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public String getName() {
        return name;
    }

    public int getMinDemandTrips() {
        return minDemandTrips;
    }

    public BigDecimal getMinDemandSupplyRatio() {
        return minDemandSupplyRatio;
    }

    public BigDecimal getMultiplier() {
        return multiplier;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private void validateWindow() {
        if (startsAt != null && endsAt != null && !startsAt.isBefore(endsAt)) {
            throw new IllegalArgumentException("startsAt must be before endsAt");
        }
    }

    private static int requireAtLeastOne(int value, String fieldName) {
        if (value < 1) {
            throw new IllegalArgumentException(fieldName + " must be at least 1");
        }
        return value;
    }

    private static BigDecimal requireMultiplier(BigDecimal value) {
        BigDecimal normalized = requirePositive(value, "multiplier");
        if (normalized.compareTo(BigDecimal.ONE) < 0 || normalized.compareTo(MAX_MULTIPLIER) > 0) {
            throw new IllegalArgumentException("multiplier must be between 1.0 and 3.0");
        }
        return normalized;
    }

    private static BigDecimal requirePositive(BigDecimal value, String fieldName) {
        BigDecimal normalized = requireNonNull(value, fieldName);
        if (normalized.signum() <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return normalized;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }
}