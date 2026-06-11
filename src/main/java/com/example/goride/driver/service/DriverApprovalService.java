package com.example.goride.driver.service;

import com.example.goride.common.api.PageResponse;
import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.driver.domain.ApprovalStatus;
import com.example.goride.driver.domain.DriverProfile;
import com.example.goride.driver.dto.DriverApprovalUpdateRequest;
import com.example.goride.driver.dto.DriverProfileResponse;
import com.example.goride.driver.repository.DriverProfileRepository;
import com.example.goride.driver.service.availability.DriverAvailabilityStore;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class DriverApprovalService {
    private static final int MAX_PAGE_SIZE = 100;

    private final DriverProfileRepository driverProfileRepository;
    private final DriverAvailabilityStore driverAvailabilityStore;

    public DriverApprovalService(
            DriverProfileRepository driverProfileRepository,
            DriverAvailabilityStore driverAvailabilityStore
    ) {
        this.driverProfileRepository = driverProfileRepository;
        this.driverAvailabilityStore = driverAvailabilityStore;
    }

    @Transactional(readOnly = true)
    public PageResponse<DriverProfileResponse> listPendingDrivers(int page, int size) {
        validatePageRequest(page, size);
        PageRequest pageRequest = PageRequest.of(
                page - 1,
                size,
                Sort.by(Sort.Direction.ASC, "createdAt")
        );
        Page<DriverProfile> profiles = driverProfileRepository.findByApprovalStatusAndUserDeletedAtIsNull(
                ApprovalStatus.PENDING,
                pageRequest
        );
        return PageResponse.of(
                profiles.getContent().stream()
                        .map(DriverProfileResponse::from)
                        .toList(),
                page,
                size,
                profiles.getTotalElements()
        );
    }

    @Transactional
    public DriverProfileResponse updateApproval(Long driverId, DriverApprovalUpdateRequest request) {
        validateApprovalRequest(driverId, request);
        DriverProfile profile = driverProfileRepository.findByUserIdForUpdate(driverId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DRIVER_PROFILE_NOT_FOUND));

        if (request.approvalStatus() == ApprovalStatus.APPROVED) {
            profile.approve();
        } else if (request.approvalStatus() == ApprovalStatus.REJECTED) {
            profile.reject();
            runAfterCommit(() -> driverAvailabilityStore.markOffline(profile.getUser().getId()));
        }

        return DriverProfileResponse.from(driverProfileRepository.save(profile));
    }

    private void validatePageRequest(int page, int size) {
        if (page < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Page must be greater than or equal to 1");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Size must be between 1 and 100");
        }
    }

    private void validateApprovalRequest(Long driverId, DriverApprovalUpdateRequest request) {
        if (driverId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Driver id is required");
        }
        if (request == null || request.approvalStatus() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Approval status is required");
        }
        if (request.approvalStatus() == ApprovalStatus.PENDING) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Approval status must be APPROVED or REJECTED");
        }
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
