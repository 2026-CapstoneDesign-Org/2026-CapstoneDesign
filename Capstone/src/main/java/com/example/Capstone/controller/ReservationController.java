package com.example.Capstone.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.Capstone.dto.request.CreateAiCallReservationRequest;
import com.example.Capstone.dto.response.ReservationListResponse;
import com.example.Capstone.dto.response.ReservationResponse;
import com.example.Capstone.service.ReservationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@Tag(name = "Reservation", description = "AI 전화 예약 API")
public class ReservationController {

    private final ReservationService reservationService;

    @Operation(summary = "AI 전화 예약 요청 생성")
    @PostMapping("/restaurants/{restaurantId}/reservations/ai-call")
    public ResponseEntity<ReservationResponse> createAiCallReservation(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long restaurantId,
            @RequestBody @Valid CreateAiCallReservationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservationService.createAiCallReservation(userId, restaurantId, request));
    }

    @Operation(summary = "내 예약 목록 조회")
    @GetMapping("/reservations")
    public ResponseEntity<ReservationListResponse> getMyReservations(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(reservationService.getMyReservations(userId));
    }

    @Operation(summary = "예약 상세 조회")
    @GetMapping("/reservations/{reservationId}")
    public ResponseEntity<ReservationResponse> getReservation(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long reservationId
    ) {
        return ResponseEntity.ok(reservationService.getReservation(userId, reservationId));
    }

    @Operation(summary = "예약 취소")
    @PatchMapping("/reservations/{reservationId}/cancel")
    public ResponseEntity<Void> cancelReservation(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long reservationId
    ) {
        reservationService.cancelReservation(userId, reservationId);
        return ResponseEntity.noContent().build();
    }

}
