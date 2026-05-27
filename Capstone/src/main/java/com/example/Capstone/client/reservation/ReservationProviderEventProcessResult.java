package com.example.Capstone.client.reservation;

import com.example.Capstone.domain.ReservationProviderEventProcessingStatus;
import com.example.Capstone.domain.ReservationStatus;

public record ReservationProviderEventProcessResult(
        ReservationProviderEventProcessingStatus processingStatus,
        boolean statusChanged,
        Long reservationId,
        ReservationStatus reservationStatus,
        String message
) {
    public static ReservationProviderEventProcessResult of(
            ReservationProviderEventProcessingStatus processingStatus,
            boolean statusChanged,
            Long reservationId,
            ReservationStatus reservationStatus,
            String message
    ) {
        return new ReservationProviderEventProcessResult(
                processingStatus,
                statusChanged,
                reservationId,
                reservationStatus,
                message
        );
    }
}
