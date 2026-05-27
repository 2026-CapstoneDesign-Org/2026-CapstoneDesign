package com.example.Capstone.client.reservation;

import java.time.LocalDateTime;

public record ReservationCallStartCommand(
        Long reservationId,
        Long restaurantId,
        String restaurantName,
        String restaurantPhoneNumber,
        LocalDateTime reservationDateTime,
        Integer partySize,
        String requestNote
) {
}
