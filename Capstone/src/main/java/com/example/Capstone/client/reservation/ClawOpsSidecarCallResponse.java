package com.example.Capstone.client.reservation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClawOpsSidecarCallResponse(
        Boolean accepted,
        String provider,
        String providerCallId,
        String providerStatus,
        String sidecarCallId,
        String idempotencyKey,
        String message
) {
    public boolean isAccepted() {
        return Boolean.TRUE.equals(accepted);
    }
}
