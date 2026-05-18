package com.example.goride.booking.dto;

import jakarta.validation.constraints.Size;

public record BookingCancelRequest(
        @Size(max = 200)
        String reason
) {
}
