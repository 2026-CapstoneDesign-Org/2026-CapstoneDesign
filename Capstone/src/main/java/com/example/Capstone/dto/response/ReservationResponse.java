package com.example.Capstone.dto.response;

import java.time.LocalDateTime;

import com.example.Capstone.domain.ReservationStatus;
import com.example.Capstone.domain.RestaurantReservation;

public record ReservationResponse(
        Long reservationId,
        Long restaurantId,
        String restaurantName,
        String restaurantAddress,
        String restaurantPhoneNumberMasked,
        LocalDateTime reservationDateTime,
        Integer partySize,
        String requestNote,
        ReservationStatus status,
        String aiSummary,
        String resultMessage,
        String failureReason,
        String provider,
        String providerCallId,
        String providerStatus,
        Integer attemptCount,
        LocalDateTime confirmedAt,
        LocalDateTime canceledAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ReservationResponse from(RestaurantReservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getRestaurant().getId(),
                reservation.getRestaurant().getName(),
                reservation.getRestaurant().getDisplayAddress(),
                maskPhoneNumber(reservation.getRestaurantPhoneNumberSnapshot()),
                reservation.getReservationDateTime(),
                reservation.getPartySize(),
                reservation.getRequestNote(),
                reservation.getStatus(),
                reservation.getAiSummary(),
                reservation.getResultMessage(),
                reservation.getFailureReason(),
                reservation.getProvider(),
                reservation.getProviderCallId(),
                reservation.getProviderStatus(),
                reservation.getAttemptCount(),
                reservation.getConfirmedAt(),
                reservation.getCanceledAt(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt()
        );
    }

    private static String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return null;
        }
        if (phoneNumber.length() <= 4) {
            return "****";
        }
        return phoneNumber.substring(0, phoneNumber.length() - 4) + "****";
    }
}
