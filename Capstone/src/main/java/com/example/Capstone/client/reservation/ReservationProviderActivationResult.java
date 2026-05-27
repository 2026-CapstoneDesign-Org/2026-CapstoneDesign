package com.example.Capstone.client.reservation;

public record ReservationProviderActivationResult(
        boolean allowed,
        String providerStatus,
        String message
) {
    public static ReservationProviderActivationResult success() {
        return new ReservationProviderActivationResult(true, null, null);
    }

    public static ReservationProviderActivationResult rejected(String providerStatus, String message) {
        return new ReservationProviderActivationResult(false, providerStatus, message);
    }
}
