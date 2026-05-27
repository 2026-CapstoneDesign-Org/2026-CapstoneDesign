package com.example.Capstone.dto.request;

public record ClawOpsRealCallPreflightRequest(
        Long reservationId,
        String targetPhoneNumber
) {
}
