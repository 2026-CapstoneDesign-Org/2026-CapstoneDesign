package com.example.Capstone.dto.response;

import com.example.Capstone.client.reservation.ReservationProviderEventProcessResult;
import com.example.Capstone.domain.ReservationProviderEventProcessingStatus;
import com.example.Capstone.domain.ReservationStatus;

public record ReservationProviderWebhookResponse(
        ReservationProviderEventProcessingStatus processingStatus,
        boolean statusChanged,
        Long reservationId,
        ReservationStatus reservationStatus,
        String message
) {
    public static ReservationProviderWebhookResponse from(ReservationProviderEventProcessResult result) {
        return new ReservationProviderWebhookResponse(
                result.processingStatus(),
                result.statusChanged(),
                result.reservationId(),
                result.reservationStatus(),
                result.message()
        );
    }
}
