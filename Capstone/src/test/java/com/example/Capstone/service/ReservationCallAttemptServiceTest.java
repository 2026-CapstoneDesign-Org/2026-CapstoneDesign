package com.example.Capstone.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.Capstone.client.reservation.ReservationCallAttemptProcessResult;
import com.example.Capstone.client.reservation.ReservationProviderEventCommand;
import com.example.Capstone.client.reservation.ReservationRetryPolicy;
import com.example.Capstone.domain.ReservationCallAttempt;
import com.example.Capstone.domain.ReservationCallAttemptStatus;
import com.example.Capstone.domain.ReservationProviderEventType;
import com.example.Capstone.domain.ReservationStatus;
import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.domain.RestaurantReservation;
import com.example.Capstone.domain.User;
import com.example.Capstone.repository.ReservationCallAttemptRepository;

@ExtendWith(MockitoExtension.class)
class ReservationCallAttemptServiceTest {

    @Mock
    private ReservationCallAttemptRepository callAttemptRepository;

    @Test
    @DisplayName("CALL_STARTED 이벤트는 새 전화 시도를 만들고 예약의 시도 횟수를 증가시킨다")
    void recordStartedCreatesAttemptAndIncrementsReservationAttemptCount() {
        ReservationCallAttemptService service = service();
        RestaurantReservation reservation = reservation();
        LocalDateTime occurredAt = LocalDateTime.of(2026, 5, 20, 12, 0);
        ReservationProviderEventCommand command = command(
                "mock-100-1",
                "event-started",
                ReservationProviderEventType.CALL_STARTED,
                occurredAt,
                false
        );

        given(callAttemptRepository.findByReservationIdAndProviderAndProviderCallId(
                100L,
                "MOCK",
                "mock-100-1"
        )).willReturn(Optional.empty());
        given(callAttemptRepository.findTopByReservationIdOrderByAttemptNumberDesc(100L))
                .willReturn(Optional.empty());
        given(callAttemptRepository.save(any(ReservationCallAttempt.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ReservationCallAttemptProcessResult result = service.recordProviderEvent(reservation, command);

        assertThat(result.attemptStatus()).isEqualTo(ReservationCallAttemptStatus.STARTED);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CALLING);
        assertThat(reservation.getAttemptCount()).isEqualTo(1);
        assertThat(reservation.getLastAttemptedAt()).isEqualTo(occurredAt);

        ArgumentCaptor<ReservationCallAttempt> captor = ArgumentCaptor.forClass(ReservationCallAttempt.class);
        then(callAttemptRepository).should().save(captor.capture());
        assertThat(captor.getValue().getAttemptNumber()).isEqualTo(1);
        assertThat(captor.getValue().getProviderCallId()).isEqualTo("mock-100-1");
    }

    @Test
    @DisplayName("retry 가능한 실패는 다음 재시도 가능 시점을 기록한다")
    void recordRetryableFailureSchedulesRetry() {
        ReservationCallAttemptService service = service();
        RestaurantReservation reservation = callingReservation();
        LocalDateTime failedAt = LocalDateTime.of(2026, 5, 20, 12, 2);
        ReservationCallAttempt attempt = attempt(reservation, 1, "mock-100-1");
        ReservationProviderEventCommand command = command(
                "mock-100-1",
                "event-no-answer",
                ReservationProviderEventType.CALL_NO_ANSWER,
                failedAt,
                true
        );

        given(callAttemptRepository.findByReservationIdAndProviderAndProviderCallId(
                100L,
                "MOCK",
                "mock-100-1"
        )).willReturn(Optional.of(attempt));

        ReservationCallAttemptProcessResult result = service.recordProviderEvent(reservation, command);

        assertThat(result.retryScheduled()).isTrue();
        assertThat(result.finalFailure()).isFalse();
        assertThat(result.nextRetryAt()).isEqualTo(failedAt.plusMinutes(1));
        assertThat(attempt.getStatus()).isEqualTo(ReservationCallAttemptStatus.RETRY_SCHEDULED);
        assertThat(attempt.getNextRetryAt()).isEqualTo(failedAt.plusMinutes(1));
    }

    @Test
    @DisplayName("retry 불가능한 실패는 최종 실패 전이 후보를 반환한다")
    void recordNonRetryableFailureReturnsFinalFailure() {
        ReservationCallAttemptService service = service();
        RestaurantReservation reservation = callingReservation();
        ReservationCallAttempt attempt = attempt(reservation, 1, "mock-100-1");
        ReservationProviderEventCommand command = command(
                "mock-100-1",
                "event-fatal",
                ReservationProviderEventType.PROVIDER_FATAL_ERROR,
                LocalDateTime.of(2026, 5, 20, 12, 2),
                false
        );

        given(callAttemptRepository.findByReservationIdAndProviderAndProviderCallId(
                100L,
                "MOCK",
                "mock-100-1"
        )).willReturn(Optional.of(attempt));

        ReservationCallAttemptProcessResult result = service.recordProviderEvent(reservation, command);

        assertThat(result.finalFailure()).isTrue();
        assertThat(result.targetStatusOverride()).isEqualTo(ReservationStatus.FAILED);
        assertThat(attempt.getStatus()).isEqualTo(ReservationCallAttemptStatus.FAILED_FINAL);
    }

    @Test
    @DisplayName("최대 시도 횟수의 retry 가능한 실패는 최종 실패로 처리한다")
    void recordRetryableFailureAtMaxAttemptsReturnsFinalFailure() {
        ReservationCallAttemptService service = service();
        RestaurantReservation reservation = callingReservation();
        ReservationCallAttempt attempt = attempt(reservation, 3, "mock-100-3");
        ReservationProviderEventCommand command = command(
                "mock-100-3",
                "event-busy-final",
                ReservationProviderEventType.CALL_BUSY,
                LocalDateTime.of(2026, 5, 20, 12, 8),
                true
        );

        given(callAttemptRepository.findByReservationIdAndProviderAndProviderCallId(
                100L,
                "MOCK",
                "mock-100-3"
        )).willReturn(Optional.of(attempt));

        ReservationCallAttemptProcessResult result = service.recordProviderEvent(reservation, command);

        assertThat(result.finalFailure()).isTrue();
        assertThat(result.targetStatusOverride()).isEqualTo(ReservationStatus.FAILED);
        assertThat(result.failureReasonOverride()).isEqualTo("통화 실패");
        assertThat(attempt.getStatus()).isEqualTo(ReservationCallAttemptStatus.FAILED_FINAL);
    }

    @Test
    @DisplayName("timeout 대상 전화 시도를 식별한다")
    void findTimeoutCandidatesUsesPolicyThreshold() {
        ReservationCallAttemptService service = service();
        LocalDateTime now = LocalDateTime.of(2026, 5, 20, 12, 30);
        ReservationCallAttempt attempt = attempt(callingReservation(), 1, "mock-100-1");
        given(callAttemptRepository.findByStatusInAndStartedAtBefore(
                any(),
                eq(now.minusMinutes(10))
        )).willReturn(List.of(attempt));

        List<ReservationCallAttempt> result = service.findTimeoutCandidates(now);

        assertThat(result).containsExactly(attempt);
    }

    @Test
    @DisplayName("scheduler no-op retry는 기존 시도를 dispatch 처리하고 새 mock 시도를 기록한다")
    void startMockRetryAttemptDispatchesPreviousAttemptAndCreatesNextAttempt() {
        ReservationCallAttemptService service = service();
        RestaurantReservation reservation = callingReservation();
        LocalDateTime retryAt = LocalDateTime.of(2026, 5, 20, 12, 5);
        ReservationCallAttempt retryAttempt = attempt(reservation, 1, "mock-100-1");
        retryAttempt.markRetryScheduled("CALL_NO_ANSWER", "부재중", retryAt, "event-no-answer");

        given(callAttemptRepository.findTopByReservationIdOrderByAttemptNumberDesc(100L))
                .willReturn(Optional.of(retryAttempt));
        given(callAttemptRepository.save(any(ReservationCallAttempt.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ReservationCallAttemptProcessResult result = service.startMockRetryAttempt(retryAttempt, retryAt);

        assertThat(result.attemptStatus()).isEqualTo(ReservationCallAttemptStatus.STARTED);
        assertThat(retryAttempt.getStatus()).isEqualTo(ReservationCallAttemptStatus.RETRY_DISPATCHED);
        assertThat(retryAttempt.getNextRetryAt()).isNull();
        assertThat(reservation.getAttemptCount()).isEqualTo(2);
        assertThat(reservation.getProviderCallId()).isEqualTo("mock-retry-100-2");

        ArgumentCaptor<ReservationCallAttempt> captor = ArgumentCaptor.forClass(ReservationCallAttempt.class);
        then(callAttemptRepository).should().save(captor.capture());
        assertThat(captor.getValue().getAttemptNumber()).isEqualTo(2);
        assertThat(captor.getValue().getProviderCallId()).isEqualTo("mock-retry-100-2");
        assertThat(captor.getValue().getStatus()).isEqualTo(ReservationCallAttemptStatus.STARTED);
    }

    @Test
    @DisplayName("timeout 시도가 재시도 가능하면 다음 재시도 시각을 기록한다")
    void handleTimeoutSchedulesRetryWhenAttemptsRemain() {
        ReservationCallAttemptService service = service();
        RestaurantReservation reservation = callingReservation();
        ReservationCallAttempt attempt = attempt(reservation, 1, "mock-100-1");
        ReflectionTestUtils.setField(attempt, "id", 501L);
        LocalDateTime timedOutAt = LocalDateTime.of(2026, 5, 20, 12, 10);

        ReservationCallAttemptProcessResult result = service.handleTimeout(attempt, timedOutAt);

        assertThat(result.retryScheduled()).isTrue();
        assertThat(result.nextRetryAt()).isEqualTo(timedOutAt.plusMinutes(1));
        assertThat(attempt.getStatus()).isEqualTo(ReservationCallAttemptStatus.RETRY_SCHEDULED);
        assertThat(attempt.getFailureCode()).isEqualTo("PROVIDER_TIMEOUT");
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CALLING);
    }

    @Test
    @DisplayName("최대 시도 횟수의 timeout은 예약을 최종 실패로 처리한다")
    void handleTimeoutAtMaxAttemptsFailsReservation() {
        ReservationCallAttemptService service = service();
        RestaurantReservation reservation = callingReservation();
        ReservationCallAttempt attempt = attempt(reservation, 3, "mock-100-3");
        ReflectionTestUtils.setField(attempt, "id", 503L);

        ReservationCallAttemptProcessResult result =
                service.handleTimeout(attempt, LocalDateTime.of(2026, 5, 20, 12, 20));

        assertThat(result.finalFailure()).isTrue();
        assertThat(result.targetStatusOverride()).isEqualTo(ReservationStatus.FAILED);
        assertThat(attempt.getStatus()).isEqualTo(ReservationCallAttemptStatus.FAILED_FINAL);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.FAILED);
        assertThat(reservation.getFailureReason()).isEqualTo("전화 provider 응답 시간이 초과되었습니다.");
    }

    private ReservationCallAttemptService service() {
        return new ReservationCallAttemptService(
                callAttemptRepository,
                new ReservationRetryPolicy(3, 60, 300, 600)
        );
    }

    private ReservationProviderEventCommand command(
            String providerCallId,
            String providerEventId,
            ReservationProviderEventType eventType,
            LocalDateTime occurredAt,
            boolean retryable
    ) {
        return ReservationProviderEventCommand.builder()
                .provider("MOCK")
                .providerCallId(providerCallId)
                .providerEventId(providerEventId)
                .reservationId(100L)
                .eventType(eventType)
                .providerStatus("MOCK_" + eventType.name())
                .occurredAt(occurredAt)
                .failureCode("CALL_FAILED")
                .failureReason("통화 실패")
                .retryable(retryable)
                .signatureVerified(true)
                .build();
    }

    private ReservationCallAttempt attempt(
            RestaurantReservation reservation,
            int attemptNumber,
            String providerCallId
    ) {
        return ReservationCallAttempt.started(
                reservation,
                attemptNumber,
                "MOCK",
                providerCallId,
                LocalDateTime.of(2026, 5, 20, 12, 0),
                "event-started"
        );
    }

    private RestaurantReservation callingReservation() {
        RestaurantReservation reservation = reservation();
        reservation.recordCallAttemptStarted(
                "MOCK",
                "mock-100-1",
                "MOCK_CALL_STARTED",
                LocalDateTime.of(2026, 5, 20, 12, 0)
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
                .reservationDateTime(LocalDateTime.now().plusDays(1))
                .partySize(4)
                .requestNote("note")
                .restaurantPhoneNumberSnapshot(restaurant.getPhoneNumber())
                .build();
        ReflectionTestUtils.setField(reservation, "id", 100L);
        return reservation;
    }
}
