package com.example.goride.driver.domain;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "driver_profiles",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_driver_profiles_user", columnNames = "user_id"),
                @UniqueConstraint(name = "uk_driver_profiles_license_number", columnNames = "license_number"),
                @UniqueConstraint(name = "uk_driver_profiles_id_card_number", columnNames = "id_card_number"),
                @UniqueConstraint(name = "uk_driver_profiles_vehicle_plate", columnNames = "vehicle_plate")
        },
        indexes = {
                @Index(name = "idx_driver_profiles_approval_status", columnList = "approval_status"),
                @Index(name = "idx_driver_profiles_online", columnList = "is_online")
        }
)
public class DriverProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "license_number", nullable = false, length = 30)
    private String licenseNumber;

    @Column(name = "license_expiry", nullable = false)
    private LocalDate licenseExpiry;

    @Column(name = "id_card_number", nullable = false, length = 20)
    private String idCardNumber;

    @Column(name = "portrait_url", nullable = false, length = 500)
    private String portraitUrl;

    @Column(name = "license_image_url", length = 500)
    private String licenseImageUrl;

    @Column(name = "id_card_image_url", length = 500)
    private String idCardImageUrl;

    @Column(name = "vehicle_registration_url", length = 500)
    private String vehicleRegistrationUrl;

    @Column(name = "vehicle_plate", nullable = false, length = 30)
    private String vehiclePlate;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private VehicleType vehicleType;

    @Column(name = "vehicle_brand", length = 50)
    private String vehicleBrand;

    @Column(name = "vehicle_model", length = 50)
    private String vehicleModel;

    @Column(name = "vehicle_color", length = 30)
    private String vehicleColor;

    @Column(name = "vehicle_year")
    private Short vehicleYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20)
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

    @Column(name = "is_online", nullable = false)
    private boolean online;

    @Column(name = "average_rating", nullable = false, precision = 2, scale = 1)
    private BigDecimal averageRating = BigDecimal.valueOf(5.0);

    @Column(name = "total_ratings", nullable = false)
    private int totalRatings;

    @Column(name = "total_trips", nullable = false)
    private int totalTrips;

    @Column(name = "last_known_location", columnDefinition = "geometry(Point,4326)")
    private Point lastKnownLocation;

    @Column(name = "last_location_at")
    private Instant lastLocationAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DriverProfile() {
    }

    public static DriverProfile create(
            User user,
            String licenseNumber,
            LocalDate licenseExpiry,
            String idCardNumber,
            String portraitUrl,
            String vehiclePlate,
            VehicleType vehicleType,
            String vehicleBrand,
            String vehicleModel,
            String vehicleColor,
            Short vehicleYear
    ) {
        DriverProfile profile = new DriverProfile();
        profile.user = requireNonNull(user, "user");
        profile.licenseNumber = requireText(licenseNumber, "licenseNumber");
        profile.licenseExpiry = requireNonNull(licenseExpiry, "licenseExpiry");
        profile.idCardNumber = requireText(idCardNumber, "idCardNumber");
        profile.portraitUrl = requireText(portraitUrl, "portraitUrl");
        profile.vehiclePlate = requireText(vehiclePlate, "vehiclePlate");
        profile.vehicleType = requireNonNull(vehicleType, "vehicleType");
        profile.vehicleBrand = normalizeOptional(vehicleBrand);
        profile.vehicleModel = normalizeOptional(vehicleModel);
        profile.vehicleColor = normalizeOptional(vehicleColor);
        profile.vehicleYear = vehicleYear;
        profile.approvalStatus = ApprovalStatus.PENDING;
        profile.online = false;
        profile.averageRating = BigDecimal.valueOf(5.0);
        return profile;
    }

    public void approve() {
        this.approvalStatus = ApprovalStatus.APPROVED;
    }

    public void reject() {
        this.approvalStatus = ApprovalStatus.REJECTED;
        this.online = false;
    }

    public void goOnline() {
        if (approvalStatus != ApprovalStatus.APPROVED) {
            throw new IllegalStateException("Driver profile must be approved before going online");
        }
        this.online = true;
    }

    public void goOffline(Point lastKnownLocation) {
        this.online = false;
        if (lastKnownLocation != null) {
            updateLastKnownLocation(lastKnownLocation);
        }
    }

    public void updateLastKnownLocation(Point location) {
        this.lastKnownLocation = location;
        this.lastLocationAt = location == null ? null : Instant.now();
    }

    public void recordHeartbeat(Point location, Instant heartbeatAt) {
        this.lastKnownLocation = requireNonNull(location, "location");
        this.lastLocationAt = requireNonNull(heartbeatAt, "heartbeatAt");
    }

    public void recordCompletedTrip() {
        this.totalTrips++;
    }

    public void updateDocumentUrls(String licenseImageUrl, String idCardImageUrl, String vehicleRegistrationUrl) {
        this.licenseImageUrl = normalizeOptional(licenseImageUrl);
        this.idCardImageUrl = normalizeOptional(idCardImageUrl);
        this.vehicleRegistrationUrl = normalizeOptional(vehicleRegistrationUrl);
    }

    public void updateAverageRating(BigDecimal averageRating, int totalRatings) {
        if (averageRating == null) {
            throw new IllegalArgumentException("averageRating must not be null");
        }
        if (totalRatings < 0) {
            throw new IllegalArgumentException("totalRatings must not be negative");
        }
        this.averageRating = averageRating;
        this.totalRatings = totalRatings;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getLicenseNumber() {
        return licenseNumber;
    }

    public LocalDate getLicenseExpiry() {
        return licenseExpiry;
    }

    public String getIdCardNumber() {
        return idCardNumber;
    }

    public String getPortraitUrl() {
        return portraitUrl;
    }

    public String getLicenseImageUrl() {
        return licenseImageUrl;
    }

    public String getIdCardImageUrl() {
        return idCardImageUrl;
    }

    public String getVehicleRegistrationUrl() {
        return vehicleRegistrationUrl;
    }

    public String getVehiclePlate() {
        return vehiclePlate;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public String getVehicleBrand() {
        return vehicleBrand;
    }

    public String getVehicleModel() {
        return vehicleModel;
    }

    public String getVehicleColor() {
        return vehicleColor;
    }

    public Short getVehicleYear() {
        return vehicleYear;
    }

    public ApprovalStatus getApprovalStatus() {
        return approvalStatus;
    }

    public boolean isOnline() {
        return online;
    }

    public BigDecimal getAverageRating() {
        return averageRating;
    }

    public int getTotalRatings() {
        return totalRatings;
    }

    public int getTotalTrips() {
        return totalTrips;
    }

    public Point getLastKnownLocation() {
        return lastKnownLocation;
    }

    public Instant getLastLocationAt() {
        return lastLocationAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
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

    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }
}
