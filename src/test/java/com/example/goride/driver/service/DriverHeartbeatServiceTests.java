package com.example.goride.driver.service;

import com.example.goride.common.error.BusinessException;
import com.example.goride.common.error.ErrorCode;
import com.example.goride.driver.domain.DriverProfile;
import com.example.goride.driver.domain.VehicleType;
import com.example.goride.driver.dto.DriverHeartbeatRequest;
import com.example.goride.driver.repository.DriverProfileRepository;
import com.example.goride.driver.service.availability.DriverAvailabilityProperties;
import com.example.goride.driver.service.availability.DriverAvailabilityStore;
import com.example.goride.user.domain.User;
import com.example.goride.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverHeartbeatServiceTests {
    private static final Instant NOW = Instant.parse("2026-06-14T02:00:00Z");

    @Mock
    private DriverProfileRepository driverProfileRepository;

    @Mock
    private DriverAvailabilityStore driverAvailabilityStore;

    private DriverHeartbeatService service;

    @BeforeEach
    void setUp() {
        service = new DriverHeartbeatService(
                driverProfileRepository,
                driverAvailabilityStore,
                new DriverAvailabilityProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void heartbeatRefreshesRedisAndPersistsLatestLocation() {
        DriverProfile profile = onlineProfile();
        when(driverProfileRepository.findByUserIdForUpdate(10L)).thenReturn(Optional.of(profile));
        when(driverAvailabilityStore.refreshHeartbeat(any())).thenReturn(true);
        when(driverProfileRepository.save(profile)).thenReturn(profile);

        var response = service.heartbeat(10L, request());

        ArgumentCaptor<DriverAvailabilityStore.DriverAvailability> captor =
                ArgumentCaptor.forClass(DriverAvailabilityStore.DriverAvailability.class);
        verify(driverAvailabilityStore).refreshHeartbeat(captor.capture());
        verify(driverProfileRepository).save(profile);
        assertThat(captor.getValue().driverId()).isEqualTo(10L);
        assertThat(captor.getValue().latitude()).isEqualByComparingTo("10.7769");
        assertThat(response.online()).isTrue();
        assertThat(response.heartbeatAt()).isEqualTo(NOW);
        assertThat(response.expiresAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(profile.getLastLocationAt()).isEqualTo(NOW);
        assertThat(profile.getLastKnownLocation().getX()).isEqualTo(106.7009);
    }

    @Test
    void heartbeatRejectsOfflineDriver() {
        DriverProfile profile = approvedProfile();
        when(driverProfileRepository.findByUserIdForUpdate(10L)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.heartbeat(10L, request()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.DRIVER_NOT_AVAILABLE)
                );
        verify(driverAvailabilityStore, never()).refreshHeartbeat(any());
        verify(driverProfileRepository, never()).save(any());
    }

    @Test
    void heartbeatRejectsExpiredRedisAvailabilityWithoutResurrectingDriver() {
        DriverProfile profile = onlineProfile();
        when(driverProfileRepository.findByUserIdForUpdate(10L)).thenReturn(Optional.of(profile));
        when(driverAvailabilityStore.refreshHeartbeat(any())).thenReturn(false);

        assertThatThrownBy(() -> service.heartbeat(10L, request()))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.DRIVER_NOT_AVAILABLE);
                    assertThat(exception.getMessage()).contains("go online again");
                });
        verify(driverProfileRepository, never()).save(any());
    }

    private DriverHeartbeatRequest request() {
        return new DriverHeartbeatRequest(
                BigDecimal.valueOf(10.7769),
                BigDecimal.valueOf(106.7009)
        );
    }

    private DriverProfile onlineProfile() {
        DriverProfile profile = approvedProfile();
        profile.goOnline();
        return profile;
    }

    private DriverProfile approvedProfile() {
        User user = User.create("Driver", "0901234567", null, "hash", Set.of(UserRole.DRIVER));
        ReflectionTestUtils.setField(user, "id", 10L);
        DriverProfile profile = DriverProfile.create(
                user,
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
        profile.approve();
        return profile;
    }
}
