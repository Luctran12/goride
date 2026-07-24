package com.example.goride.booking.domain;

import com.example.goride.driver.domain.VehicleType;
import com.example.goride.user.domain.User;
import com.example.goride.user.domain.UserRole;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "trips",
        indexes = {
                @Index(name = "idx_trips_status", columnList = "status"),
                @Index(name = "idx_trips_scheduled_pickup_time", columnList = "status, scheduled_pickup_time"),
                @Index(name = "idx_trips_passenger_requested_at", columnList = "passenger_id, requested_at"),
                @Index(name = "idx_trips_driver_requested_at", columnList = "driver_id, requested_at")
        }
)
public class Trip {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "passenger_id", nullable = false)
    private User passenger;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private User driver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TripStatus status = TripStatus.SEARCHING;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private VehicleType vehicleType;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod = PaymentMethod.CASH;

    @Column(name = "pickup_address", nullable = false, length = 300)
    private String pickupAddress;

    @Column(name = "pickup_location", nullable = false, columnDefinition = "geometry(Point,4326)")
    private Point pickupLocation;

    @Column(name = "dropoff_address", nullable = false, length = 300)
    private String dropoffAddress;

    @Column(name = "dropoff_location", nullable = false, columnDefinition = "geometry(Point,4326)")
    private Point dropoffLocation;

    @Column(name = "estimated_distance_km", nullable = false, precision = 8, scale = 2)
    private BigDecimal estimatedDistanceKm;

    @Column(name = "estimated_duration_min", nullable = false)
    private int estimatedDurationMin;

    @Column(name = "actual_distance_km", precision = 8, scale = 2)
    private BigDecimal actualDistanceKm;

    @Column(name = "actual_duration_min")
    private Integer actualDurationMin;

    @Column(name = "estimated_fare", nullable = false, precision = 10, scale = 0)
    private BigDecimal estimatedFare;

    @Column(name = "final_fare", precision = 10, scale = 0)
    private BigDecimal finalFare;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pricing_config_id", nullable = false)
    private PricingConfig pricingConfig;

    @Column(
            name = "fare_surge_multiplier",
            nullable = false,
            precision = 4,
            scale = 2,
            columnDefinition = "numeric(4,2) default 1.00"
    )
    private BigDecimal fareSurgeMultiplier = BigDecimal.ONE;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "scheduled_pickup_time")
    private Instant scheduledPickupTime;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "arrived_at")
    private Instant arrivedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason", length = 200)
    private String cancelReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Trip() {
    }

    public static Trip create(
            User passenger,
            VehicleType vehicleType,
            PaymentMethod paymentMethod,
            String pickupAddress,
            Point pickupLocation,
            String dropoffAddress,
            Point dropoffLocation,
            BigDecimal estimatedDistanceKm,
            int estimatedDurationMin,
            BigDecimal estimatedFare,
            PricingConfig pricingConfig
    ) {
        PricingConfig normalizedPricingConfig = requireNonNull(pricingConfig, "pricingConfig");
        return create(
                passenger,
                vehicleType,
                paymentMethod,
                pickupAddress,
                pickupLocation,
                dropoffAddress,
                dropoffLocation,
                estimatedDistanceKm,
                estimatedDurationMin,
                estimatedFare,
                normalizedPricingConfig,
                normalizedPricingConfig.getSurgeMultiplier()
        );
    }

    public static Trip create(
            User passenger,
            VehicleType vehicleType,
            PaymentMethod paymentMethod,
            String pickupAddress,
            Point pickupLocation,
            String dropoffAddress,
            Point dropoffLocation,
            BigDecimal estimatedDistanceKm,
            int estimatedDurationMin,
            BigDecimal estimatedFare,
            PricingConfig pricingConfig,
            BigDecimal fareSurgeMultiplier
    ) {
        if (estimatedDurationMin <= 0) {
            throw new IllegalArgumentException("estimatedDurationMin must be positive");
        }

        User normalizedPassenger = requireNonNull(passenger, "passenger");
        if (!normalizedPassenger.hasRole(UserRole.PASSENGER)) {
            throw new IllegalArgumentException("Passenger user must have PASSENGER role");
        }

        PricingConfig normalizedPricingConfig = requireNonNull(pricingConfig, "pricingConfig");
        VehicleType normalizedVehicleType = requireNonNull(vehicleType, "vehicleType");
        if (normalizedPricingConfig.getVehicleType() != normalizedVehicleType) {
            throw new IllegalArgumentException("Pricing config vehicle type must match trip vehicle type");
        }

        Trip trip = new Trip();
        trip.passenger = normalizedPassenger;
        trip.status = TripStatus.SEARCHING;
        trip.vehicleType = normalizedVehicleType;
        trip.paymentMethod = paymentMethod == null ? PaymentMethod.CASH : paymentMethod;
        trip.pickupAddress = requireText(pickupAddress, "pickupAddress");
        trip.pickupLocation = requireNonNull(pickupLocation, "pickupLocation");
        trip.dropoffAddress = requireText(dropoffAddress, "dropoffAddress");
        trip.dropoffLocation = requireNonNull(dropoffLocation, "dropoffLocation");
        trip.estimatedDistanceKm = requirePositive(estimatedDistanceKm, "estimatedDistanceKm");
        trip.estimatedDurationMin = estimatedDurationMin;
        trip.estimatedFare = requirePositiveOrZero(estimatedFare, "estimatedFare");
        trip.pricingConfig = normalizedPricingConfig;
        trip.fareSurgeMultiplier = requirePositive(fareSurgeMultiplier, "fareSurgeMultiplier");
        trip.requestedAt = Instant.now();
        return trip;
    }

    public static Trip createScheduled(
            User passenger,
            VehicleType vehicleType,
            PaymentMethod paymentMethod,
            String pickupAddress,
            Point pickupLocation,
            String dropoffAddress,
            Point dropoffLocation,
            BigDecimal estimatedDistanceKm,
            int estimatedDurationMin,
            BigDecimal estimatedFare,
            PricingConfig pricingConfig,
            Instant scheduledPickupTime
    ) {
        PricingConfig normalizedPricingConfig = requireNonNull(pricingConfig, "pricingConfig");
        return createScheduled(
                passenger,
                vehicleType,
                paymentMethod,
                pickupAddress,
                pickupLocation,
                dropoffAddress,
                dropoffLocation,
                estimatedDistanceKm,
                estimatedDurationMin,
                estimatedFare,
                normalizedPricingConfig,
                normalizedPricingConfig.getSurgeMultiplier(),
                scheduledPickupTime
        );
    }

    public static Trip createScheduled(
            User passenger,
            VehicleType vehicleType,
            PaymentMethod paymentMethod,
            String pickupAddress,
            Point pickupLocation,
            String dropoffAddress,
            Point dropoffLocation,
            BigDecimal estimatedDistanceKm,
            int estimatedDurationMin,
            BigDecimal estimatedFare,
            PricingConfig pricingConfig,
            BigDecimal fareSurgeMultiplier,
            Instant scheduledPickupTime
    ) {
        Trip trip = create(
                passenger,
                vehicleType,
                paymentMethod,
                pickupAddress,
                pickupLocation,
                dropoffAddress,
                dropoffLocation,
                estimatedDistanceKm,
                estimatedDurationMin,
                estimatedFare,
                pricingConfig,
                fareSurgeMultiplier
        );
        trip.status = TripStatus.SCHEDULED;
        trip.scheduledPickupTime = requireNonNull(scheduledPickupTime, "scheduledPickupTime");
        return trip;
    }

    public void dispatchScheduled() {
        if (status != TripStatus.SCHEDULED) {
            throw new IllegalStateException("Only scheduled trips can be dispatched");
        }

        this.status = TripStatus.SEARCHING;
    }

    public void accept(User driver) {
        if (status != TripStatus.SEARCHING) {
            throw new IllegalStateException("Trip can only be accepted while searching");
        }

        User normalizedDriver = requireNonNull(driver, "driver");
        if (!normalizedDriver.hasRole(UserRole.DRIVER)) {
            throw new IllegalArgumentException("Driver user must have DRIVER role");
        }

        this.driver = normalizedDriver;
        this.status = TripStatus.ACCEPTED;
        this.acceptedAt = Instant.now();
    }

    public void markArrived() {
        if (status != TripStatus.ACCEPTED) {
            throw new IllegalStateException("Trip can only be marked arrived after acceptance");
        }

        this.status = TripStatus.ARRIVED;
        this.arrivedAt = Instant.now();
    }

    public void startTrip() {
        if (status != TripStatus.ARRIVED) {
            throw new IllegalStateException("Trip can only be started after driver arrival");
        }

        this.status = TripStatus.IN_PROGRESS;
        this.startedAt = Instant.now();
    }

    public void complete(BigDecimal finalFare, BigDecimal actualDistanceKm, int actualDurationMin) {
        if (status != TripStatus.IN_PROGRESS) {
            throw new IllegalStateException("Trip can only be completed while in progress");
        }
        if (actualDurationMin <= 0) {
            throw new IllegalArgumentException("actualDurationMin must be positive");
        }

        this.status = TripStatus.COMPLETED;
        this.finalFare = requirePositiveOrZero(finalFare, "finalFare");
        this.actualDistanceKm = requirePositive(actualDistanceKm, "actualDistanceKm");
        this.actualDurationMin = actualDurationMin;
        this.completedAt = Instant.now();
    }

    public void cancel(String reason) {
        if (!status.canBeCancelled()) {
            throw new IllegalStateException("Trip cannot be cancelled in its current status");
        }

        this.status = TripStatus.CANCELLED;
        this.cancelReason = normalizeOptional(reason);
        this.cancelledAt = Instant.now();
    }

    public void markNoDriver() {
        if (status != TripStatus.SEARCHING) {
            throw new IllegalStateException("Trip can only be marked no driver while searching");
        }

        this.status = TripStatus.NO_DRIVER;
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (requestedAt == null) {
            requestedAt = now;
        }
        if (fareSurgeMultiplier == null) {
            fareSurgeMultiplier = pricingConfig == null ? BigDecimal.ONE : pricingConfig.getSurgeMultiplier();
        }
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

    public User getPassenger() {
        return passenger;
    }

    public User getDriver() {
        return driver;
    }

    public TripStatus getStatus() {
        return status;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public String getPickupAddress() {
        return pickupAddress;
    }

    public Point getPickupLocation() {
        return pickupLocation;
    }

    public String getDropoffAddress() {
        return dropoffAddress;
    }

    public Point getDropoffLocation() {
        return dropoffLocation;
    }

    public BigDecimal getEstimatedDistanceKm() {
        return estimatedDistanceKm;
    }

    public int getEstimatedDurationMin() {
        return estimatedDurationMin;
    }

    public BigDecimal getActualDistanceKm() {
        return actualDistanceKm;
    }

    public Integer getActualDurationMin() {
        return actualDurationMin;
    }

    public BigDecimal getEstimatedFare() {
        return estimatedFare;
    }

    public BigDecimal getFinalFare() {
        return finalFare;
    }

    public PricingConfig getPricingConfig() {
        return pricingConfig;
    }

    public BigDecimal getFareSurgeMultiplier() {
        return fareSurgeMultiplier;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getScheduledPickupTime() {
        return scheduledPickupTime;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public Instant getArrivedAt() {
        return arrivedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static BigDecimal requirePositive(BigDecimal value, String fieldName) {
        BigDecimal normalized = requireNonNull(value, fieldName);
        if (normalized.signum() <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return normalized;
    }

    private static BigDecimal requirePositiveOrZero(BigDecimal value, String fieldName) {
        BigDecimal normalized = requireNonNull(value, fieldName);
        if (normalized.signum() < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
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
