package com.example.Capstone.client.reservation;

import java.time.LocalDateTime;

public record PhoneProviderCallStartCommand(
        ReservationProviderMode providerMode,
        Long reservationId,
        Long restaurantId,
        String restaurantName,
        String toPhoneNumber,
        LocalDateTime reservationDateTime,
        Integer partySize,
        String requestNote,
        String openAiSessionId
) {
    public static PhoneProviderCallStartCommand from(
            ReservationProviderMode providerMode,
            ReservationCallStartCommand command,
            OpenAiRealtimeSessionResult openAiSession
    ) {
        return new PhoneProviderCallStartCommand(
                providerMode,
                command.reservationId(),
                command.restaurantId(),
                command.restaurantName(),
                command.restaurantPhoneNumber(),
                command.reservationDateTime(),
                command.partySize(),
                command.requestNote(),
                openAiSession == null ? null : openAiSession.providerSessionId()
        );
    }

    public PhoneProviderCallStartCommand withToPhoneNumber(String targetPhoneNumber) {
        return new PhoneProviderCallStartCommand(
                providerMode,
                reservationId,
                restaurantId,
                restaurantName,
                targetPhoneNumber,
                reservationDateTime,
                partySize,
                requestNote,
                openAiSessionId
        );
    }
}
