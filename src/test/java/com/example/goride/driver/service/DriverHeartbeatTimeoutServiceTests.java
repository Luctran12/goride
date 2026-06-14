package com.example.goride.driver.service;

import com.example.goride.driver.domain.DriverProfile;
import com.example.goride.driver.domain.VehicleType;
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
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverHeartbeatTimeoutServiceTests {
    private static final Instant NOW = Instant.parse("2026-06-14T02:00:00Z");

    @Mock
    private DriverProfileRepository driverProfileRepository;

    @Mock
    private DriverAvailabilityStore driverAvailabilityStore;

    private DriverHeartbeatTimeoutService service;

    @BeforeEach
    void setUp() {
        DriverAvailabilityProperties properties = new DriverAvailabilityProperties();
        properties.setCleanupBatchSize(25);
        service = new DriverHeartbeatTimeoutService(
                driverProfileRepository,
                driverAvailabilityStore,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void expiresStaleProfilesInConfiguredBatch() {
        DriverProfile first = onlineProfile(10L);
        DriverProfile second = onlineProfile(11L);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(driverProfileRepository.findStaleOnlineProfilesForUpdate(
                org.mockito.ArgumentMatchers.eq(NOW.minusSeconds(60)),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(List.of(first, second));

        int expired = service.expireStaleDrivers();

        verify(driverProfileRepository).findStaleOnlineProfilesForUpdate(
                org.mockito.ArgumentMatchers.eq(NOW.minusSeconds(60)),
                pageableCaptor.capture()
        );
        verify(driverProfileRepository).saveAll(List.of(first, second));
        verify(driverAvailabilityStore).markOffline(10L);
        verify(driverAvailabilityStore).markOffline(11L);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(25);
        assertThat(first.isOnline()).isFalse();
        assertThat(second.isOnline()).isFalse();
        assertThat(expired).isEqualTo(2);
    }

    @Test
    void doesNothingWhenNoProfileIsStale() {
        when(driverProfileRepository.findStaleOnlineProfilesForUpdate(
                org.mockito.ArgumentMatchers.any(Instant.class),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(List.of());

        int expired = service.expireStaleDrivers();

        assertThat(expired).isZero();
        verify(driverAvailabilityStore, never()).markOffline(org.mockito.ArgumentMatchers.any());
    }

    private DriverProfile onlineProfile(Long userId) {
        User user = User.create("Driver " + userId, "09012345" + userId, null, "hash", Set.of(UserRole.DRIVER));
        ReflectionTestUtils.setField(user, "id", userId);
        DriverProfile profile = DriverProfile.create(
                user,
                "GPLX" + userId,
                LocalDate.now().plusYears(2),
                "0123456789" + userId,
                "https://example.com/portrait.jpg",
                "51A-" + userId,
                VehicleType.CAR_4_SEAT,
                "Toyota",
                "Vios",
                "White",
                (short) 2022
        );
        profile.approve();
        profile.goOnline();
        return profile;
    }
}
