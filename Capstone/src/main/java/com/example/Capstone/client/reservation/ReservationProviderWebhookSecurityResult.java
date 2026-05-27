package com.example.Capstone.client.reservation;

public record ReservationProviderWebhookSecurityResult(
        boolean verified,
        String failureReason
) {
    public static ReservationProviderWebhookSecurityResult success() {
        return new ReservationProviderWebhookSecurityResult(true, null);
    }

    public static ReservationProviderWebhookSecurityResult rejected(String failureReason) {
        return new ReservationProviderWebhookSecurityResult(false, failureReason);
    }
}
