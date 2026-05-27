package com.example.Capstone.dto.request;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAiCallReservationRequest(
        @NotNull LocalDate reservationDate,
        @NotNull LocalTime reservationTime,
        @NotNull @Min(1) @Max(20) Integer partySize,
        @Size(max = 500) String requestNote
) {
}
