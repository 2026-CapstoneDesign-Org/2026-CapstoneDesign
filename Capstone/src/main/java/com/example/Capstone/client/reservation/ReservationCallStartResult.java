package com.example.Capstone.client.reservation;

import com.example.Capstone.domain.ReservationStatus;

public record ReservationCallStartResult(
        ReservationStatus status,
        String provider,
        String providerCallId,
        String providerStatus
) {
}
