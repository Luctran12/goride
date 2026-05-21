package com.example.goride.driver.dto;

import com.example.goride.matching.domain.DriverOfferDecision;
import jakarta.validation.constraints.NotNull;

public record DriverTripRespondRequest(
        @NotNull DriverOfferDecision action
) {
}
