package com.example.goride.user.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_phone", columnNames = "phone"),
                @UniqueConstraint(name = "uk_users_email", columnNames = "email")
        },
        indexes = {
                @Index(name = "idx_users_status", columnList = "status")
        }
)
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 120)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_user_roles_user_role", columnNames = {"user_id", "role"})
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Set<UserRole> roles = EnumSet.noneOf(UserRole.class);

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected User() {
    }

    public static User create(
            String fullName,
            String phone,
            String email,
            String passwordHash,
            Set<UserRole> roles
    ) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("User must have at least one role");
        }

        User user = new User();
        user.fullName = requireText(fullName, "fullName");
        user.phone = requireText(phone, "phone");
        user.email = normalizeOptional(email);
        user.passwordHash = requireText(passwordHash, "passwordHash");
        user.roles = EnumSet.copyOf(roles);
        user.status = UserStatus.ACTIVE;
        return user;
    }

    public void updateProfile(String fullName, String email) {
        this.fullName = requireText(fullName, "fullName");
        this.email = normalizeOptional(email);
    }

    public void updateDetails(String fullName, String phone, String email, String avatarUrl) {
        this.fullName = requireText(fullName, "fullName");
        this.phone = requireText(phone, "phone");
        this.email = normalizeOptional(email);
        this.avatarUrl = normalizeOptional(avatarUrl);
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = requireText(passwordHash, "passwordHash");
    }

    public void replaceRoles(Set<UserRole> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("User must have at least one role");
        }
        this.roles = EnumSet.copyOf(roles);
    }

    public void addRole(UserRole role) {
        this.roles.add(Objects.requireNonNull(role, "role"));
    }

    public boolean hasRole(UserRole role) {
        return this.roles.contains(role);
    }

    public void suspend() {
        this.status = UserStatus.SUSPENDED;
    }

    public void activate() {
        this.status = UserStatus.ACTIVE;
    }

    public void changeStatus(UserStatus status) {
        this.status = Objects.requireNonNull(status, "status");
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

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Set<UserRole> getRoles() {
        return Collections.unmodifiableSet(roles);
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
}
