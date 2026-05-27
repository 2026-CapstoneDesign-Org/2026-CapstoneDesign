package com.example.Capstone.dto.request;

import java.time.LocalDateTime;

import com.example.Capstone.domain.ReservationProviderEventType;
import com.example.Capstone.domain.ReservationStatus;

public record MockReservationProviderWebhookRequest(
        Long reservationId,
        String providerCallId,
        String providerEventId,
        ReservationProviderEventType eventType,
        String providerStatus,
        LocalDateTime occurredAt,
        ReservationStatus targetStatus,
        String failureCode,
        String failureReason,
        Boolean retryable,
        String aiSummary,
        String resultMessage
) {
}
