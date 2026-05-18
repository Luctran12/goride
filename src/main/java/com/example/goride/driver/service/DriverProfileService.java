package com.example.goride.driver.service;

import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.driver.domain.DriverProfile;
import com.example.goride.driver.dto.DriverProfileResponse;
import com.example.goride.driver.dto.DriverProfileUpsertRequest;
import com.example.goride.driver.repository.DriverProfileRepository;
import com.example.goride.user.domain.User;
import com.example.goride.user.domain.UserRole;
import com.example.goride.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriverProfileService {
    private final DriverProfileRepository driverProfileRepository;
    private final UserRepository userRepository;

    public DriverProfileService(DriverProfileRepository driverProfileRepository, UserRepository userRepository) {
        this.driverProfileRepository = driverProfileRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public DriverProfileResponse getMyProfile(Long userId) {
        return driverProfileRepository.findByUserIdAndUserDeletedAtIsNull(userId)
                .map(DriverProfileResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.DRIVER_PROFILE_NOT_FOUND));
    }

    @Transactional
    public DriverProfileResponse createMyProfile(Long userId, DriverProfileUpsertRequest request) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!user.hasRole(UserRole.DRIVER)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only driver users can create a driver profile");
        }
        if (driverProfileRepository.existsByUserId(userId)) {
            throw new BusinessException(ErrorCode.DRIVER_PROFILE_ALREADY_EXISTS);
        }
        validateUniqueDocuments(request);

        DriverProfile profile = DriverProfile.create(
                user,
                request.licenseNumber(),
                request.licenseExpiry(),
                request.idCardNumber(),
                request.portraitUrl(),
                request.vehiclePlate(),
                request.vehicleType(),
                request.vehicleBrand(),
                request.vehicleModel(),
                request.vehicleColor(),
                request.vehicleYear()
        );

        return DriverProfileResponse.from(driverProfileRepository.save(profile));
    }

    private void validateUniqueDocuments(DriverProfileUpsertRequest request) {
        if (driverProfileRepository.existsByLicenseNumber(request.licenseNumber())) {
            throw new BusinessException(ErrorCode.LICENSE_NUMBER_ALREADY_EXISTS);
        }
        if (driverProfileRepository.existsByIdCardNumber(request.idCardNumber())) {
            throw new BusinessException(ErrorCode.ID_CARD_ALREADY_EXISTS);
        }
        if (driverProfileRepository.existsByVehiclePlate(request.vehiclePlate())) {
            throw new BusinessException(ErrorCode.VEHICLE_PLATE_ALREADY_EXISTS);
        }
    }
}
