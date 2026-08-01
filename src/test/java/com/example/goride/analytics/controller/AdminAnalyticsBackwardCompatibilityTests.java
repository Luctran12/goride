package com.example.goride.analytics.controller;

import com.example.goride.admin.controller.AdminDashboardController;
import com.example.goride.admin.dto.AdminDashboardResponse;
import com.example.goride.common.api.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.ParameterizedType;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAnalyticsBackwardCompatibilityTests {
    @Test
    void legacyDashboardPathAndResponseShapeRemainFrozen() throws NoSuchMethodException {
        RequestMapping requestMapping = AnnotatedElementUtils.findMergedAnnotation(
                AdminDashboardController.class,
                RequestMapping.class
        );
        GetMapping getMapping = AnnotatedElementUtils.findMergedAnnotation(
                AdminDashboardController.class.getMethod("getDashboard"),
                GetMapping.class
        );

        assertThat(requestMapping).isNotNull();
        assertThat(requestMapping.value()).containsExactly("/api/v1/admin/dashboard");
        assertThat(getMapping).isNotNull();
        assertThat(getMapping.value()).isEmpty();
        assertThat(Arrays.stream(AdminDashboardResponse.class.getRecordComponents())
                .map(component -> component.getName()))
                .containsExactly(
                        "totalUsers",
                        "activeUsers",
                        "suspendedUsers",
                        "totalDrivers",
                        "pendingDrivers",
                        "approvedDrivers",
                        "rejectedDrivers",
                        "totalTrips",
                        "tripsByStatus",
                        "completedPayments",
                        "completedRevenue",
                        "averageDriverRating"
                );

        ParameterizedType returnType = (ParameterizedType) AdminDashboardController.class
                .getMethod("getDashboard")
                .getGenericReturnType();
        assertThat(returnType.getRawType()).isEqualTo(ApiResponse.class);
        assertThat(returnType.getActualTypeArguments())
                .containsExactly(AdminDashboardResponse.class);
    }
}
