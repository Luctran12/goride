package com.example.goride.admin.dto;

import com.example.goride.booking.domain.TripStatus;

import java.math.BigDecimal;
import java.util.Map;

public record AdminDashboardResponse(
        long totalUsers,
        long activeUsers,
        long suspendedUsers,
        long totalDrivers,
        long pendingDrivers,
        long approvedDrivers,
        long rejectedDrivers,
        long totalTrips,
        Map<TripStatus, Long> tripsByStatus,
        long completedPayments,
        BigDecimal completedRevenue,
        BigDecimal averageDriverRating
) {
}
