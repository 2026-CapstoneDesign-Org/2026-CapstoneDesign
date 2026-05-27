package com.example.Capstone.dto.request;

import com.example.Capstone.domain.ReservationStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ApplyReservationMockResultRequest(
        @NotNull ReservationStatus status,
        @Size(max = 2000) String aiSummary,
        @Size(max = 1000) String resultMessage,
        @Size(max = 500) String failureReason,
        @Size(max = 100) String providerCallId
) {
}
