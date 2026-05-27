package com.example.Capstone.client.reservation;

import java.time.LocalDateTime;

public record OpenAiRealtimeSessionCommand(
        Long reservationId,
        String restaurantName,
        LocalDateTime reservationDateTime,
        Integer partySize,
        String requestNote
) {
    public static OpenAiRealtimeSessionCommand from(ReservationCallStartCommand command) {
        return new OpenAiRealtimeSessionCommand(
                command.reservationId(),
                command.restaurantName(),
                command.reservationDateTime(),
                command.partySize(),
                command.requestNote()
        );
    }
}
