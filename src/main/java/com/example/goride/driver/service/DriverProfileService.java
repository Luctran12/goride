package com.example.goride.driver.service;

import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.driver.domain.ApprovalStatus;
import com.example.goride.driver.domain.DriverProfile;
import com.example.goride.driver.dto.DriverProfileResponse;
import com.example.goride.driver.dto.DriverStatusUpdateRequest;
import com.example.goride.driver.dto.DriverProfileUpsertRequest;
import com.example.goride.driver.event.DriverAvailableEvent;
import com.example.goride.driver.repository.DriverProfileRepository;
import com.example.goride.driver.service.availability.DriverAvailabilityStore;
import com.example.goride.user.domain.User;
import com.example.goride.user.domain.UserRole;
import com.example.goride.user.repository.UserRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;

@Service
public class DriverProfileService {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private final DriverProfileRepository driverProfileRepository;
    private final UserRepository userRepository;
    private final DriverAvailabilityStore driverAvailabilityStore;
    private final ApplicationEventPublisher eventPublisher;

    public DriverProfileService(
            DriverProfileRepository driverProfileRepository,
            UserRepository userRepository,
            DriverAvailabilityStore driverAvailabilityStore,
            ApplicationEventPublisher eventPublisher
    ) {
        this.driverProfileRepository = driverProfileRepository;
        this.userRepository = userRepository;
        this.driverAvailabilityStore = driverAvailabilityStore;
        this.eventPublisher = eventPublisher;
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
        profile.updateDocumentUrls(
                request.licenseImageUrl(),
                request.idCardImageUrl(),
                request.vehicleRegistrationUrl()
        );

        return DriverProfileResponse.from(driverProfileRepository.save(profile));
    }

    @Transactional
    public DriverProfileResponse updateMyStatus(Long userId, DriverStatusUpdateRequest request) {
        DriverProfile profile = driverProfileRepository.findByUserIdAndUserDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DRIVER_PROFILE_NOT_FOUND));

        if (Boolean.TRUE.equals(request.online())) {
            return goOnline(profile, request);
        }
        return goOffline(profile, request);
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

    private DriverProfileResponse goOnline(DriverProfile profile, DriverStatusUpdateRequest request) {
        if (profile.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new BusinessException(ErrorCode.DRIVER_NOT_APPROVED);
        }
        validateCompleteLocation(request);

        Point location = toPoint(request.lat(), request.lng());
        profile.goOnline();
        profile.updateLastKnownLocation(location);
        DriverProfile savedProfile = driverProfileRepository.save(profile);
        DriverAvailabilityStore.DriverAvailability availability = availabilityFrom(savedProfile, request.lat(), request.lng());
        Long driverId = savedProfile.getUser().getId();
        runAfterCommit(() -> {
            driverAvailabilityStore.markAvailable(availability);
            eventPublisher.publishEvent(new DriverAvailableEvent(driverId));
        });
        return DriverProfileResponse.from(savedProfile);
    }

    private DriverProfileResponse goOffline(DriverProfile profile, DriverStatusUpdateRequest request) {
        Point location = hasAnyLocation(request) ? toPoint(request.lat(), request.lng()) : null;
        profile.goOffline(location);
        DriverProfile savedProfile = driverProfileRepository.save(profile);
        Long driverId = savedProfile.getUser().getId();
        runAfterCommit(() -> driverAvailabilityStore.markOffline(driverId));
        return DriverProfileResponse.from(savedProfile);
    }

    private DriverAvailabilityStore.DriverAvailability availabilityFrom(
            DriverProfile profile,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        User user = profile.getUser();
        return new DriverAvailabilityStore.DriverAvailability(
                user.getId(),
                latitude,
                longitude,
                profile.getVehicleType(),
                profile.getAverageRating(),
                user.getFullName(),
                user.getAvatarUrl()
        );
    }

    private void validateCompleteLocation(DriverStatusUpdateRequest request) {
        if (!hasCompleteLocation(request)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Latitude and longitude are required when driver goes online"
            );
        }
    }

    private boolean hasAnyLocation(DriverStatusUpdateRequest request) {
        return request.lat() != null || request.lng() != null;
    }

    private boolean hasCompleteLocation(DriverStatusUpdateRequest request) {
        return request.lat() != null && request.lng() != null;
    }

    private Point toPoint(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Both latitude and longitude are required");
        }
        return GEOMETRY_FACTORY.createPoint(new Coordinate(longitude.doubleValue(), latitude.doubleValue()));
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
