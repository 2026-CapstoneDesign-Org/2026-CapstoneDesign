package com.example.Capstone.client.reservation;

import org.springframework.stereotype.Component;

import com.example.Capstone.dto.request.MockReservationProviderWebhookRequest;

@Component
public class MockReservationProviderWebhookMapper {

    public ReservationProviderEventCommand toCommand(
            String provider,
            MockReservationProviderWebhookRequest request,
            String rawPayload
    ) {
        if (request.eventType() == null) {
            throw new IllegalArgumentException("eventType은 필수입니다.");
        }
        return ReservationProviderEventCommand.builder()
                .provider(provider == null ? null : provider.toUpperCase())
                .providerCallId(request.providerCallId())
                .providerEventId(request.providerEventId())
                .reservationId(request.reservationId())
                .eventType(request.eventType())
                .providerStatus(request.providerStatus())
                .occurredAt(request.occurredAt())
                .targetStatus(request.targetStatus())
                .failureCode(request.failureCode())
                .failureReason(request.failureReason())
                .retryable(Boolean.TRUE.equals(request.retryable()))
                .aiSummary(request.aiSummary())
                .resultMessage(request.resultMessage())
                .rawPayload(rawPayload)
                .signatureVerified(true)
                .build();
    }
}
