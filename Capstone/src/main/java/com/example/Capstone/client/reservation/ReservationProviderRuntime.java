package com.example.Capstone.client.reservation;

public enum ReservationProviderRuntime {
    DIRECT_REST,
    CLAWOPS_SIDECAR;

    public boolean isClawOpsSidecar() {
        return this == CLAWOPS_SIDECAR;
    }
}
