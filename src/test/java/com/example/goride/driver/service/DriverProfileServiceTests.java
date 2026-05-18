package com.example.goride.driver.service;

import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.driver.domain.DriverProfile;
import com.example.goride.driver.domain.VehicleType;
import com.example.goride.driver.dto.DriverProfileUpsertRequest;
import com.example.goride.driver.repository.DriverProfileRepository;
import com.example.goride.user.domain.User;
import com.example.goride.user.domain.UserRole;
import com.example.goride.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverProfileServiceTests {
    @Mock
    private DriverProfileRepository driverProfileRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DriverProfileService driverProfileService;

    @Test
    void createMyProfilePersistsPendingProfileForDriverUser() {
        User driver = withUserId(User.create("Driver", "0901234567", null, "hash", Set.of(UserRole.DRIVER)), 10L);
        when(userRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(driver));
        when(driverProfileRepository.save(any(DriverProfile.class))).thenAnswer(invocation ->
                withProfileId(invocation.getArgument(0), 20L)
        );

        var response = driverProfileService.createMyProfile(10L, request());

        ArgumentCaptor<DriverProfile> profileCaptor = ArgumentCaptor.forClass(DriverProfile.class);
        verify(driverProfileRepository).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getUser()).isSameAs(driver);
        assertThat(response.id()).isEqualTo(20L);
        assertThat(response.userId()).isEqualTo(10L);
        assertThat(response.vehicleType()).isEqualTo(VehicleType.CAR_4_SEAT);
        assertThat(response.online()).isFalse();
    }

    @Test
    void createMyProfileRejectsNonDriverUser() {
        User passenger = withUserId(User.create("Passenger", "0901234567", null, "hash", Set.of(UserRole.PASSENGER)), 10L);
        when(userRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(passenger));

        assertThatThrownBy(() -> driverProfileService.createMyProfile(10L, request()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN)
                );
        verify(driverProfileRepository, never()).save(any(DriverProfile.class));
    }

    @Test
    void createMyProfileRejectsDuplicateVehiclePlate() {
        User driver = withUserId(User.create("Driver", "0901234567", null, "hash", Set.of(UserRole.DRIVER)), 10L);
        when(userRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(driver));
        when(driverProfileRepository.existsByVehiclePlate("51A-123.45")).thenReturn(true);

        assertThatThrownBy(() -> driverProfileService.createMyProfile(10L, request()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VEHICLE_PLATE_ALREADY_EXISTS)
                );
    }

    @Test
    void getMyProfileReturnsExistingProfile() {
        User driver = withUserId(User.create("Driver", "0901234567", null, "hash", Set.of(UserRole.DRIVER)), 10L);
        DriverProfile profile = withProfileId(DriverProfile.create(
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
        ), 20L);
        when(driverProfileRepository.findByUserIdAndUserDeletedAtIsNull(10L)).thenReturn(Optional.of(profile));

        var response = driverProfileService.getMyProfile(10L);

        assertThat(response.id()).isEqualTo(20L);
        assertThat(response.userId()).isEqualTo(10L);
        assertThat(response.licenseNumber()).isEqualTo("GPLX123456");
    }

    private DriverProfileUpsertRequest request() {
        return new DriverProfileUpsertRequest(
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
    }

    private User withUserId(User user, Long id) {
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private DriverProfile withProfileId(DriverProfile profile, Long id) {
        ReflectionTestUtils.setField(profile, "id", id);
        return profile;
    }
}
