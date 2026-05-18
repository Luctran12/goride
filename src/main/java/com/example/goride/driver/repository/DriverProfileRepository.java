package com.example.goride.driver.repository;

import com.example.goride.driver.domain.ApprovalStatus;
import com.example.goride.driver.domain.DriverProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DriverProfileRepository extends JpaRepository<DriverProfile, Long> {
    Optional<DriverProfile> findByUserIdAndUserDeletedAtIsNull(Long userId);

    boolean existsByUserId(Long userId);

    boolean existsByLicenseNumber(String licenseNumber);

    boolean existsByIdCardNumber(String idCardNumber);

    boolean existsByVehiclePlate(String vehiclePlate);

    long countByApprovalStatus(ApprovalStatus approvalStatus);
}
