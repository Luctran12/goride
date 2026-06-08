package com.example.goride.admin.service;

import com.example.goride.admin.dto.AdminDashboardResponse;
import com.example.goride.booking.domain.TripStatus;
import com.example.goride.booking.repository.TripRepository;
import com.example.goride.driver.domain.ApprovalStatus;
import com.example.goride.driver.repository.DriverProfileRepository;
import com.example.goride.payment.domain.PaymentStatus;
import com.example.goride.payment.repository.PaymentRepository;
import com.example.goride.user.domain.UserStatus;
import com.example.goride.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;

@Service
public class AdminDashboardService {
    private final UserRepository userRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final TripRepository tripRepository;
    private final PaymentRepository paymentRepository;

    public AdminDashboardService(
            UserRepository userRepository,
            DriverProfileRepository driverProfileRepository,
            TripRepository tripRepository,
            PaymentRepository paymentRepository
    ) {
        this.userRepository = userRepository;
        this.driverProfileRepository = driverProfileRepository;
        this.tripRepository = tripRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboard() {
        return new AdminDashboardResponse(
                userRepository.countByDeletedAtIsNull(),
                userRepository.countByStatusAndDeletedAtIsNull(UserStatus.ACTIVE),
                userRepository.countByStatusAndDeletedAtIsNull(UserStatus.SUSPENDED),
                driverProfileRepository.countByUserDeletedAtIsNull(),
                driverProfileRepository.countByApprovalStatusAndUserDeletedAtIsNull(ApprovalStatus.PENDING),
                driverProfileRepository.countByApprovalStatusAndUserDeletedAtIsNull(ApprovalStatus.APPROVED),
                driverProfileRepository.countByApprovalStatusAndUserDeletedAtIsNull(ApprovalStatus.REJECTED),
                tripRepository.countByDeletedAtIsNull(),
                tripsByStatus(),
                paymentRepository.countByStatus(PaymentStatus.COMPLETED),
                paymentRepository.sumAmountByStatus(PaymentStatus.COMPLETED),
                averageDriverRating()
        );
    }

    private Map<TripStatus, Long> tripsByStatus() {
        Map<TripStatus, Long> counts = new EnumMap<>(TripStatus.class);
        for (TripStatus status : TripStatus.values()) {
            counts.put(status, 0L);
        }
        tripRepository.countTripsByStatus()
                .forEach(count -> counts.put(count.getStatus(), count.getTotal()));
        return counts;
    }

    private BigDecimal averageDriverRating() {
        return BigDecimal.valueOf(driverProfileRepository.averageRatingByUserDeletedAtIsNull())
                .setScale(1, RoundingMode.HALF_UP);
    }
}
