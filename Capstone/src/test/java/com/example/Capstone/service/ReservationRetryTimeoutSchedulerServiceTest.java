package com.example.Capstone.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.Capstone.client.reservation.ReservationCallAttemptProcessResult;
import com.example.Capstone.client.reservation.ReservationSchedulerRunResult;
import com.example.Capstone.common.scheduler.ReservationSchedulerExecutionGuard;
import com.example.Capstone.domain.ReservationCallAttempt;
import com.example.Capstone.domain.ReservationCallAttemptStatus;
import com.example.Capstone.domain.ReservationStatus;
import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.domain.RestaurantReservation;
import com.example.Capstone.domain.User;

@ExtendWith(MockitoExtension.class)
class ReservationRetryTimeoutSchedulerServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 20, 12, 0);

    @Mock
    private ReservationCallAttemptService callAttemptService;

    private final ReservationSchedulerExecutionGuard executionGuard = new ReservationSchedulerExecutionGuard();

    @AfterEach
    void tearDown() {
        executionGuard.finish("reservation-call-attempt:retry:501");
        executionGuard.finish("reservation-call-attempt:timeout:501");
    }

    @Test
    @DisplayName("retry scheduler는 재시도 대상 시도를 식별하고 no-op mock retry를 dispatch한다")
    void processRetryReadyAttemptsDispatchesRetry() {
        ReservationRetryTimeoutSchedulerService service = service();
        ReservationCallAttempt attempt = retryReadyAttempt(callingReservation(), 501L);
        given(callAttemptService.findRetryReadyAttempts(NOW)).willReturn(List.of(attempt));
        given(callAttemptService.startMockRetryAttempt(attempt, NOW))
                .willReturn(ReservationCallAttemptProcessResult.of(
                        ReservationCallAttemptStatus.STARTED,
                        false,
                        false,
                        null,
                        null,
                        null
                ));

        ReservationSchedulerRunResult result = service.processRetryReadyAttempts(NOW);

        assertThat(result.retryCandidates()).isEqualTo(1);
        assertThat(result.retriesDispatched()).isEqualTo(1);
        assertThat(result.skipped()).isZero();
        then(callAttemptService).should().startMockRetryAttempt(attempt, NOW);
    }

    @Test
    @DisplayName("terminal 예약의 retry 대상은 상태를 덮어쓰지 않고 제외한다")
    void processRetryReadyAttemptsSkipsTerminalReservation() {
        ReservationRetryTimeoutSchedulerService service = service();
        ReservationCallAttempt attempt = retryReadyAttempt(confirmedReservation(), 501L);
        given(callAttemptService.findRetryReadyAttempts(NOW)).willReturn(List.of(attempt));

        ReservationSchedulerRunResult result = service.processRetryReadyAttempts(NOW);

        assertThat(result.retryCandidates()).isEqualTo(1);
        assertThat(result.retriesDispatched()).isZero();
        assertThat(result.skipped()).isEqualTo(1);
        then(callAttemptService).should(never()).startMockRetryAttempt(any(), any());
    }

    @Test
    @DisplayName("timeout scheduler는 timeout 대상 시도를 식별하고 timeout 처리를 호출한다")
    void processTimeoutCandidatesHandlesTimeout() {
        ReservationRetryTimeoutSchedulerService service = service();
        ReservationCallAttempt attempt = startedAttempt(callingReservation(), 501L);
        given(callAttemptService.findTimeoutCandidates(NOW)).willReturn(List.of(attempt));
        given(callAttemptService.handleTimeout(attempt, NOW))
                .willReturn(ReservationCallAttemptProcessResult.of(
                        ReservationCallAttemptStatus.RETRY_SCHEDULED,
                        true,
                        false,
                        NOW.plusMinutes(1),
                        null,
                        null
                ));

        ReservationSchedulerRunResult result = service.processTimeoutCandidates(NOW);

        assertThat(result.timeoutCandidates()).isEqualTo(1);
        assertThat(result.timeoutsScheduled()).isEqualTo(1);
        assertThat(result.timeoutsFailed()).isZero();
        assertThat(result.skipped()).isZero();
        then(callAttemptService).should().handleTimeout(attempt, NOW);
    }

    @Test
    @DisplayName("terminal 예약의 timeout 대상은 상태를 덮어쓰지 않고 제외한다")
    void processTimeoutCandidatesSkipsTerminalReservation() {
        ReservationRetryTimeoutSchedulerService service = service();
        ReservationCallAttempt attempt = startedAttempt(confirmedReservation(), 501L);
        given(callAttemptService.findTimeoutCandidates(NOW)).willReturn(List.of(attempt));

        ReservationSchedulerRunResult result = service.processTimeoutCandidates(NOW);

        assertThat(result.timeoutCandidates()).isEqualTo(1);
        assertThat(result.timeoutsScheduled()).isZero();
        assertThat(result.timeoutsFailed()).isZero();
        assertThat(result.skipped()).isEqualTo(1);
        then(callAttemptService).should(never()).handleTimeout(any(), any());
    }

    @Test
    @DisplayName("같은 retry 대상이 이미 처리 중이면 중복 dispatch하지 않는다")
    void processRetryReadyAttemptsSkipsAlreadyRunningAttempt() {
        ReservationRetryTimeoutSchedulerService service = service();
        ReservationCallAttempt attempt = retryReadyAttempt(callingReservation(), 501L);
        given(callAttemptService.findRetryReadyAttempts(NOW)).willReturn(List.of(attempt));

        assertThat(executionGuard.tryStart("reservation-call-attempt:retry:501")).isTrue();

        ReservationSchedulerRunResult result = service.processRetryReadyAttempts(NOW);

        assertThat(result.retryCandidates()).isEqualTo(1);
        assertThat(result.retriesDispatched()).isZero();
        assertThat(result.skipped()).isEqualTo(1);
        then(callAttemptService).should(never()).startMockRetryAttempt(any(), eq(NOW));
    }

    private ReservationRetryTimeoutSchedulerService service() {
        return new ReservationRetryTimeoutSchedulerService(callAttemptService, executionGuard);
    }

    private ReservationCallAttempt retryReadyAttempt(RestaurantReservation reservation, Long id) {
        ReservationCallAttempt attempt = startedAttempt(reservation, id);
        attempt.markRetryScheduled("CALL_NO_ANSWER", "부재중", NOW.minusMinutes(1), "event-no-answer");
        return attempt;
    }

    private ReservationCallAttempt startedAttempt(RestaurantReservation reservation, Long id) {
        ReservationCallAttempt attempt = ReservationCallAttempt.started(
                reservation,
                1,
                "MOCK",
                "mock-100-1",
                NOW.minusMinutes(10),
                "event-started"
        );
        ReflectionTestUtils.setField(attempt, "id", id);
        return attempt;
    }

    private RestaurantReservation confirmedReservation() {
        RestaurantReservation reservation = callingReservation();
        reservation.applyProviderEvent(
                ReservationStatus.CONFIRMED,
                "MOCK",
                "mock-100-1",
                "MOCK_CONFIRMED",
                "예약 확정",
                "예약 가능합니다.",
                null
        );
        return reservation;
    }

    private RestaurantReservation callingReservation() {
        RestaurantReservation reservation = reservation();
        reservation.recordCallAttemptStarted(
                "MOCK",
                "mock-100-1",
                "MOCK_CALL_STARTED",
                NOW.minusMinutes(10)
        );
        return reservation;
    }

    private RestaurantReservation reservation() {
        User user = User.builder()
                .provider("KAKAO")
                .providerUserId("provider-1")
                .nickname("user-1")
                .profileImageUrl("profile")
                .role(User.Role.USER)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);

        Restaurant restaurant = Restaurant.builder()
                .name("Restaurant 10")
                .address("Address 10")
                .roadAddress("Road 10")
                .categoryName("Korean")
                .regionName("Seoul")
                .lat(new BigDecimal("37.0"))
                .lng(new BigDecimal("127.0"))
                .phoneNumber("02-1234-5678")
                .build();
        ReflectionTestUtils.setField(restaurant, "id", 10L);

        RestaurantReservation reservation = RestaurantReservation.builder()
                .user(user)
                .restaurant(restaurant)
                .reservationDateTime(NOW.plusDays(1))
                .partySize(4)
                .requestNote("note")
                .restaurantPhoneNumberSnapshot(restaurant.getPhoneNumber())
                .build();
        ReflectionTestUtils.setField(reservation, "id", 100L);
        return reservation;
    }
}
