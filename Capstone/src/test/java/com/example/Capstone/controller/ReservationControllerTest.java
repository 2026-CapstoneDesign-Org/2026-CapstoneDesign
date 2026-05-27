package com.example.Capstone.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.Capstone.domain.ReservationStatus;
import com.example.Capstone.dto.request.CreateAiCallReservationRequest;
import com.example.Capstone.dto.response.ReservationListResponse;
import com.example.Capstone.dto.response.ReservationResponse;
import com.example.Capstone.exception.BusinessException;
import com.example.Capstone.exception.GlobalExceptionHandler;
import com.example.Capstone.service.ReservationService;

@ExtendWith(MockitoExtension.class)
class ReservationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReservationController(reservationService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("인증 사용자가 AI 전화 예약 요청을 생성한다")
    void createAiCallReservation() throws Exception {
        authenticateAs(1L);
        when(reservationService.createAiCallReservation(
                eq(1L),
                eq(10L),
                org.mockito.ArgumentMatchers.any(CreateAiCallReservationRequest.class)
        )).thenReturn(response(100L, ReservationStatus.REQUESTED));

        mockMvc.perform(post("/restaurants/10/reservations/ai-call")
                        .contentType("application/json")
                        .content("""
                                {
                                  "reservationDate": "2026-06-01",
                                  "reservationTime": "19:00",
                                  "partySize": 4,
                                  "requestNote": "창가 자리 요청"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationId").value(100))
                .andExpect(jsonPath("$.restaurantId").value(10))
                .andExpect(jsonPath("$.status").value("REQUESTED"));

        verify(reservationService).createAiCallReservation(
                eq(1L),
                eq(10L),
                org.mockito.ArgumentMatchers.any(CreateAiCallReservationRequest.class)
        );
    }

    @Test
    @DisplayName("인증되지 않은 요청은 서비스 검증을 통해 401로 반환된다")
    void createAiCallReservationRequiresAuthentication() throws Exception {
        when(reservationService.createAiCallReservation(
                eq(null),
                eq(10L),
                org.mockito.ArgumentMatchers.any(CreateAiCallReservationRequest.class)
        )).thenThrow(new BusinessException("인증이 필요합니다.", HttpStatus.UNAUTHORIZED));

        mockMvc.perform(post("/restaurants/10/reservations/ai-call")
                        .contentType("application/json")
                        .content("""
                                {
                                  "reservationDate": "2026-06-01",
                                  "reservationTime": "19:00",
                                  "partySize": 4
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("내 예약 목록을 조회한다")
    void getMyReservations() throws Exception {
        authenticateAs(1L);
        when(reservationService.getMyReservations(1L))
                .thenReturn(new ReservationListResponse(List.of(response(100L, ReservationStatus.CONFIRMED))));

        mockMvc.perform(get("/reservations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].reservationId").value(100))
                .andExpect(jsonPath("$.items[0].status").value("CONFIRMED"));

        verify(reservationService).getMyReservations(1L);
    }

    @Test
    @DisplayName("예약 상세를 조회한다")
    void getReservation() throws Exception {
        authenticateAs(1L);
        when(reservationService.getReservation(1L, 100L))
                .thenReturn(response(100L, ReservationStatus.NEEDS_CONFIRMATION));

        mockMvc.perform(get("/reservations/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(100))
                .andExpect(jsonPath("$.status").value("NEEDS_CONFIRMATION"));

        verify(reservationService).getReservation(1L, 100L);
    }

    @Test
    @DisplayName("예약을 취소한다")
    void cancelReservation() throws Exception {
        authenticateAs(1L);

        mockMvc.perform(patch("/reservations/100/cancel"))
                .andExpect(status().isNoContent());

        verify(reservationService).cancelReservation(1L, 100L);
    }

    @Test
    @DisplayName("비정상 인원 수는 controller validation에서 400으로 반환된다")
    void createAiCallReservationRejectsInvalidPartySize() throws Exception {
        authenticateAs(1L);

        mockMvc.perform(post("/restaurants/10/reservations/ai-call")
                        .contentType("application/json")
                        .content("""
                                {
                                  "reservationDate": "2026-06-01",
                                  "reservationTime": "19:00",
                                  "partySize": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private void authenticateAs(Long userId) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(userId, "token"));
        SecurityContextHolder.setContext(context);
    }

    private ReservationResponse response(Long reservationId, ReservationStatus status) {
        return new ReservationResponse(
                reservationId,
                10L,
                "Sample Restaurant",
                "Sample Address",
                "02-1234-****",
                LocalDateTime.of(2026, 6, 1, 19, 0),
                4,
                "창가 자리 요청",
                status,
                status == ReservationStatus.CONFIRMED ? "예약 가능 확인" : null,
                status == ReservationStatus.CONFIRMED ? "예약이 확정되었습니다." : null,
                null,
                "MOCK",
                "mock-" + reservationId,
                "MOCK_" + status.name(),
                0,
                null,
                null,
                LocalDateTime.of(2026, 5, 20, 10, 0),
                LocalDateTime.of(2026, 5, 20, 10, 0)
        );
    }
}
