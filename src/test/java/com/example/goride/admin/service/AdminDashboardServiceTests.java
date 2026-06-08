package com.example.goride.admin.service;

import com.example.goride.booking.domain.TripStatus;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.driver.domain.ApprovalStatus;
import com.example.goride.driver.repository.DriverProfileRepository;
import com.example.goride.payment.domain.PaymentStatus;
import com.example.goride.payment.repository.PaymentRepository;
import com.example.goride.user.domain.UserStatus;
import com.example.goride.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTests {
    @Mock
    private UserRepository userRepository;

    @Mock
    private DriverProfileRepository driverProfileRepository;

    @Mock
    private TripRepository tripRepository;

    @Mock
    private PaymentRepository paymentRepository;

    private AdminDashboardService service;

    @BeforeEach
    void setUp() {
        service = new AdminDashboardService(
                userRepository,
                driverProfileRepository,
                tripRepository,
                paymentRepository
        );
    }

    @Test
    void buildsDashboardFromRepositoryAggregates() {
        when(userRepository.countByDeletedAtIsNull()).thenReturn(12L);
        when(userRepository.countByStatusAndDeletedAtIsNull(UserStatus.ACTIVE)).thenReturn(10L);
        when(userRepository.countByStatusAndDeletedAtIsNull(UserStatus.SUSPENDED)).thenReturn(2L);
        when(driverProfileRepository.countByUserDeletedAtIsNull()).thenReturn(5L);
        when(driverProfileRepository.countByApprovalStatusAndUserDeletedAtIsNull(ApprovalStatus.PENDING))
                .thenReturn(1L);
        when(driverProfileRepository.countByApprovalStatusAndUserDeletedAtIsNull(ApprovalStatus.APPROVED))
                .thenReturn(3L);
        when(driverProfileRepository.countByApprovalStatusAndUserDeletedAtIsNull(ApprovalStatus.REJECTED))
                .thenReturn(1L);
        when(tripRepository.countByDeletedAtIsNull()).thenReturn(20L);
        when(tripRepository.countTripsByStatus()).thenReturn(List.of(
                count(TripStatus.COMPLETED, 8L),
                count(TripStatus.CANCELLED, 2L)
        ));
        when(paymentRepository.countByStatus(PaymentStatus.COMPLETED)).thenReturn(7L);
        when(paymentRepository.sumAmountByStatus(PaymentStatus.COMPLETED)).thenReturn(BigDecimal.valueOf(350000));
        when(driverProfileRepository.averageRatingByUserDeletedAtIsNull()).thenReturn(4.34);

        var response = service.getDashboard();

        assertThat(response.totalUsers()).isEqualTo(12L);
        assertThat(response.activeUsers()).isEqualTo(10L);
        assertThat(response.suspendedUsers()).isEqualTo(2L);
        assertThat(response.totalDrivers()).isEqualTo(5L);
        assertThat(response.pendingDrivers()).isEqualTo(1L);
        assertThat(response.approvedDrivers()).isEqualTo(3L);
        assertThat(response.rejectedDrivers()).isEqualTo(1L);
        assertThat(response.totalTrips()).isEqualTo(20L);
        assertThat(response.tripsByStatus()).containsEntry(TripStatus.COMPLETED, 8L);
        assertThat(response.tripsByStatus()).containsEntry(TripStatus.CANCELLED, 2L);
        assertThat(response.tripsByStatus()).containsEntry(TripStatus.SEARCHING, 0L);
        assertThat(response.completedPayments()).isEqualTo(7L);
        assertThat(response.completedRevenue()).isEqualByComparingTo(BigDecimal.valueOf(350000));
        assertThat(response.averageDriverRating()).isEqualByComparingTo(BigDecimal.valueOf(4.3));
    }

    private TripRepository.TripStatusCount count(TripStatus status, long total) {
        return new TripRepository.TripStatusCount() {
            @Override
            public TripStatus getStatus() {
                return status;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }
}
