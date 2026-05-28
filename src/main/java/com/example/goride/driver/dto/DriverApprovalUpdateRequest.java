package com.example.goride.driver.dto;

import com.example.goride.driver.domain.ApprovalStatus;
import jakarta.validation.constraints.NotNull;

public record DriverApprovalUpdateRequest(
        @NotNull ApprovalStatus approvalStatus
) {
}
