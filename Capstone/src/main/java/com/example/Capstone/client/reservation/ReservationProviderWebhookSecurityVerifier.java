package com.example.Capstone.client.reservation;

public interface ReservationProviderWebhookSecurityVerifier {

    ReservationProviderWebhookSecurityResult verify(ReservationProviderWebhookPayload payload);
}
