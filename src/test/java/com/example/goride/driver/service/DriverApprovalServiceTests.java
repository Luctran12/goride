package com.example.goride.driver.service;

import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.driver.domain.ApprovalStatus;
import com.example.goride.driver.domain.DriverProfile;
import com.example.goride.driver.domain.VehicleType;
import com.example.goride.driver.dto.DriverApprovalUpdateRequest;
import com.example.goride.driver.repository.DriverProfileRepository;
import com.example.goride.driver.service.availability.DriverAvailabilityStore;
import com.example.goride.user.domain.User;
import com.example.goride.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverApprovalServiceTests {
    @Mock
    private DriverProfileRepository driverProfileRepository;

    @Mock
    private DriverAvailabilityStore driverAvailabilityStore;

    private DriverApprovalService service;

    @BeforeEach
    void setUp() {
        service = new DriverApprovalService(driverProfileRepository, driverAvailabilityStore);
    }

    @Test
    void listsPendingDriversWithOneBasedPagination() {
        DriverProfile profile = profile(driver(10L));
        when(driverProfileRepository.findByApprovalStatusAndUserDeletedAtIsNull(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(profile), org.springframework.data.domain.PageRequest.of(1, 2), 3));

        var response = service.listPendingDrivers(2, 2);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<ApprovalStatus> statusCaptor = ArgumentCaptor.forClass(ApprovalStatus.class);
        verify(driverProfileRepository).findByApprovalStatusAndUserDeletedAtIsNull(
                statusCaptor.capture(),
                pageableCaptor.capture()
        );
        assertThat(statusCaptor.getValue()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(2);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("createdAt").isAscending()).isTrue();
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).userId()).isEqualTo(10L);
        assertThat(response.pagination().page()).isEqualTo(2);
        assertThat(response.pagination().size()).isEqualTo(2);
        assertThat(response.pagination().totalItems()).isEqualTo(3);
        assertThat(response.pagination().totalPages()).isEqualTo(2);
    }

    @Test
    void approvesDriverProfile() {
        DriverProfile profile = profile(driver(10L));
        when(driverProfileRepository.findByUserIdForUpdate(10L)).thenReturn(Optional.of(profile));
        when(driverProfileRepository.save(profile)).thenReturn(profile);

        var response = service.updateApproval(10L, new DriverApprovalUpdateRequest(ApprovalStatus.APPROVED));

        verify(driverProfileRepository).save(profile);
        verify(driverAvailabilityStore, never()).markOffline(any());
        assertThat(profile.getApprovalStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(response.userId()).isEqualTo(10L);
        assertThat(response.approvalStatus()).isEqualTo(ApprovalStatus.APPROVED);
    }

    @Test
    void rejectsDriverProfileAndMarksDriverOffline() {
        DriverProfile profile = profile(driver(10L));
        profile.approve();
        profile.goOnline();
        when(driverProfileRepository.findByUserIdForUpdate(10L)).thenReturn(Optional.of(profile));
        when(driverProfileRepository.save(profile)).thenReturn(profile);

        var response = service.updateApproval(10L, new DriverApprovalUpdateRequest(ApprovalStatus.REJECTED));

        verify(driverProfileRepository).save(profile);
        verify(driverAvailabilityStore).markOffline(10L);
        assertThat(profile.getApprovalStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(profile.isOnline()).isFalse();
        assertThat(response.approvalStatus()).isEqualTo(ApprovalStatus.REJECTED);
    }

    @Test
    void rejectsPendingApprovalStatusRequest() {
        assertThatThrownBy(() -> service.updateApproval(10L, new DriverApprovalUpdateRequest(ApprovalStatus.PENDING)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR)
                );

        verifyNoInteractions(driverProfileRepository, driverAvailabilityStore);
    }

    @Test
    void rejectsMissingDriverProfile() {
        when(driverProfileRepository.findByUserIdForUpdate(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateApproval(10L, new DriverApprovalUpdateRequest(ApprovalStatus.APPROVED)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.DRIVER_PROFILE_NOT_FOUND)
                );

        verify(driverProfileRepository, never()).save(any());
        verifyNoInteractions(driverAvailabilityStore);
    }

    @Test
    void rejectsInvalidPaginationBeforeQuerying() {
        assertThatThrownBy(() -> service.listPendingDrivers(0, 20))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR)
                );
        assertThatThrownBy(() -> service.listPendingDrivers(1, 101))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR)
                );

        verifyNoInteractions(driverProfileRepository, driverAvailabilityStore);
    }

    private DriverProfile profile(User driver) {
        DriverProfile profile = DriverProfile.create(
                driver,
                "GPLX123456",
                LocalDate.now().plusYears(2),
                "012345678901",
                "https://example.com/portrait.jpg",
                "51A-123.45",
                VehicleType.CAR_4_SEAT,
                "Toyota",
                "Vios",
                "White",
                (short) 2022
        );
        ReflectionTestUtils.setField(profile, "id", 20L);
        return profile;
    }

    private User driver(Long id) {
        User user = User.create("Driver", "0901234567", null, "hash", Set.of(UserRole.DRIVER));
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
