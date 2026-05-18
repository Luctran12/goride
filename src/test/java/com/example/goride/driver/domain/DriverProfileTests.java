package com.example.goride.driver.domain;

import com.example.goride.user.domain.User;
import com.example.goride.user.domain.UserRole;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DriverProfileTests {
    @Test
    void createStartsPendingAndOffline() {
        DriverProfile profile = sampleProfile();

        assertThat(profile.getApprovalStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(profile.isOnline()).isFalse();
        assertThat(profile.getAverageRating()).isEqualByComparingTo(BigDecimal.valueOf(5.0));
        assertThat(profile.getVehiclePlate()).isEqualTo("51A-123.45");
    }

    @Test
    void cannotGoOnlineBeforeApproval() {
        DriverProfile profile = sampleProfile();

        assertThatThrownBy(profile::goOnline)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Driver profile must be approved before going online");
    }

    @Test
    void approvedDriverCanGoOnlineAndRejectedDriverIsForcedOffline() {
        DriverProfile profile = sampleProfile();

        profile.approve();
        profile.goOnline();
        profile.reject();

        assertThat(profile.getApprovalStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(profile.isOnline()).isFalse();
    }

    @Test
    void updateAverageRatingRejectsNegativeCount() {
        DriverProfile profile = sampleProfile();

        assertThatThrownBy(() -> profile.updateAverageRating(BigDecimal.valueOf(4.8), -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("totalRatings must not be negative");
    }

    private DriverProfile sampleProfile() {
        User user = User.create(
                "Nguyen Van Driver",
                "0901234567",
                null,
                "encoded-password",
                Set.of(UserRole.DRIVER)
        );

        return DriverProfile.create(
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
    }
}
