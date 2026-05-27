package com.example.Capstone.client.reservation;

import java.time.LocalDateTime;

import com.example.Capstone.domain.ReservationProviderEventType;
import com.example.Capstone.domain.ReservationStatus;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReservationProviderEventCommand {

    private final String provider;
    private final String providerCallId;
    private final String providerEventId;
    private final Long reservationId;
    private final ReservationProviderEventType eventType;
    private final String providerStatus;
    private final LocalDateTime occurredAt;
    private final ReservationStatus targetStatus;
    private final String failureCode;
    private final String failureReason;
    private final boolean retryable;
    private final String aiSummary;
    private final String resultMessage;
    private final String rawPayloadHash;
    private final String rawPayload;
    private final boolean signatureVerified;

    public ReservationStatus resolvedTargetStatus() {
        if (targetStatus != null) {
            return targetStatus;
        }
        if (eventType == null) {
            return null;
        }
        return eventType.defaultTargetStatus(retryable);
    }
}
