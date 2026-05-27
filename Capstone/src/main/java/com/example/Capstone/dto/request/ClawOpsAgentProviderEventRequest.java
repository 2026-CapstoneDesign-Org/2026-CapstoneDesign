package com.example.Capstone.dto.request;

import java.time.LocalDateTime;

import com.example.Capstone.domain.ReservationProviderEventType;

public record ClawOpsAgentProviderEventRequest(
        String provider,
        Long reservationId,
        String providerCallId,
        String sidecarCallId,
        ReservationProviderEventType eventType,
        String providerStatus,
        LocalDateTime occurredAt,
        Boolean retryable,
        String failureReason,
        String aiSummary,
        String resultMessage,
        String idempotencyKey,
        String rawPayloadHash
) {
}
