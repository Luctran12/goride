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
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Entity
@Table(
        name = "pricing_config",
        indexes = {
                @Index(name = "idx_pricing_config_active_vehicle", columnList = "vehicle_type, is_active, effective_from")
        }
)
public class PricingConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private VehicleType vehicleType;

    @Column(name = "base_fare", nullable = false, precision = 10, scale = 0)
    private BigDecimal baseFare;

    @Column(name = "per_km_rate", nullable = false, precision = 8, scale = 0)
    private BigDecimal perKmRate;

    @Column(name = "per_minute_rate", nullable = false, precision = 6, scale = 0)
    private BigDecimal perMinuteRate = BigDecimal.ZERO;

    @Column(name = "minimum_fare", nullable = false, precision = 10, scale = 0)
    private BigDecimal minimumFare;

    @Column(name = "surge_multiplier", nullable = false, precision = 3, scale = 1)
    private BigDecimal surgeMultiplier = BigDecimal.ONE;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PricingConfig() {
    }

    public static PricingConfig create(
            VehicleType vehicleType,
            BigDecimal baseFare,
            BigDecimal perKmRate,
            BigDecimal perMinuteRate,
            BigDecimal minimumFare,
            BigDecimal surgeMultiplier,
            Instant effectiveFrom
    ) {
        PricingConfig pricingConfig = new PricingConfig();
        pricingConfig.vehicleType = requireNonNull(vehicleType, "vehicleType");
        pricingConfig.baseFare = requirePositiveOrZero(baseFare, "baseFare");
        pricingConfig.perKmRate = requirePositiveOrZero(perKmRate, "perKmRate");
        pricingConfig.perMinuteRate = requirePositiveOrZero(perMinuteRate, "perMinuteRate");
        pricingConfig.minimumFare = requirePositiveOrZero(minimumFare, "minimumFare");
        pricingConfig.surgeMultiplier = requirePositive(surgeMultiplier, "surgeMultiplier");
        pricingConfig.effectiveFrom = effectiveFrom == null ? Instant.now() : effectiveFrom;
        pricingConfig.active = true;
        return pricingConfig;
    }

    public BigDecimal estimateFare(BigDecimal distanceKm, int durationMinutes) {
        if (durationMinutes < 0) {
            throw new IllegalArgumentException("durationMinutes must not be negative");
        }

        BigDecimal normalizedDistance = requirePositiveOrZero(distanceKm, "distanceKm");
        BigDecimal fare = baseFare
                .add(normalizedDistance.multiply(perKmRate))
                .add(BigDecimal.valueOf(durationMinutes).multiply(perMinuteRate));

        if (fare.compareTo(minimumFare) < 0) {
            fare = minimumFare;
        }

        return fare.multiply(surgeMultiplier).setScale(0, RoundingMode.HALF_UP);
    }

    public void deactivate() {
        this.active = false;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (effectiveFrom == null) {
            effectiveFrom = now;
        }
        createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public BigDecimal getBaseFare() {
        return baseFare;
    }

    public BigDecimal getPerKmRate() {
        return perKmRate;
    }

    public BigDecimal getPerMinuteRate() {
        return perMinuteRate;
    }

    public BigDecimal getMinimumFare() {
        return minimumFare;
    }

    public BigDecimal getSurgeMultiplier() {
        return surgeMultiplier;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private static BigDecimal requirePositiveOrZero(BigDecimal value, String fieldName) {
        BigDecimal normalized = requireNonNull(value, fieldName);
        if (normalized.signum() < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
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

    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }
}
