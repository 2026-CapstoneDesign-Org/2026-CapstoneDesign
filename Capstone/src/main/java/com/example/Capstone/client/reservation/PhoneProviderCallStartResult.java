package com.example.Capstone.client.reservation;

import com.example.Capstone.domain.ReservationStatus;

public record PhoneProviderCallStartResult(
        boolean started,
        ReservationStatus reservationStatus,
        String provider,
        String providerCallId,
        String providerStatus,
        String message
) {
    public static PhoneProviderCallStartResult disabled(
            Long reservationId,
            String providerStatus,
            String message
    ) {
        return new PhoneProviderCallStartResult(
                false,
                ReservationStatus.REQUESTED,
                "NOOP",
                "noop-" + reservationId,
                providerStatus,
                message
        );
    }

    public ReservationCallStartResult toReservationCallStartResult() {
        return new ReservationCallStartResult(
                reservationStatus,
                provider,
                providerCallId,
                providerStatus
        );
    }
}
