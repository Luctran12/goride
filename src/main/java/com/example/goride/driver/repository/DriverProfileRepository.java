package com.example.goride.driver.repository;

import com.example.goride.driver.domain.ApprovalStatus;
import com.example.goride.driver.domain.DriverProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DriverProfileRepository extends JpaRepository<DriverProfile, Long> {
    Optional<DriverProfile> findByUserIdAndUserDeletedAtIsNull(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select profile
            from DriverProfile profile
            join fetch profile.user user
            where user.id = :userId
              and user.deletedAt is null
            """)
    Optional<DriverProfile> findByUserIdForUpdate(@Param("userId") Long userId);

    Page<DriverProfile> findByApprovalStatusAndUserDeletedAtIsNull(
            ApprovalStatus approvalStatus,
            Pageable pageable
    );

    boolean existsByUserId(Long userId);

    boolean existsByLicenseNumber(String licenseNumber);

    boolean existsByIdCardNumber(String idCardNumber);

    boolean existsByVehiclePlate(String vehiclePlate);

    long countByApprovalStatus(ApprovalStatus approvalStatus);

    long countByUserDeletedAtIsNull();

    long countByApprovalStatusAndUserDeletedAtIsNull(ApprovalStatus approvalStatus);

    @Query("""
            select coalesce(avg(profile.averageRating), 0)
            from DriverProfile profile
            where profile.user.deletedAt is null
            """)
    double averageRatingByUserDeletedAtIsNull();
}
