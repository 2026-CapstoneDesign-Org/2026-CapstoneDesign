package com.example.Capstone.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.Capstone.client.reservation.ReservationProviderEventCommand;
import com.example.Capstone.client.reservation.ReservationProviderEventIdempotencyKeyFactory;
import com.example.Capstone.client.reservation.ReservationProviderEventProcessResult;
import com.example.Capstone.domain.ReservationProviderEvent;
import com.example.Capstone.domain.ReservationProviderEventProcessingStatus;
import com.example.Capstone.domain.ReservationProviderEventType;
import com.example.Capstone.domain.ReservationStatus;
import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.domain.RestaurantReservation;
import com.example.Capstone.domain.User;
import com.example.Capstone.repository.ReservationProviderEventRepository;
import com.example.Capstone.repository.RestaurantReservationRepository;

@ExtendWith(MockitoExtension.class)
class ReservationProviderEventServiceTest {

    @Mock
    private ReservationProviderEventRepository providerEventRepository;

    @Mock
    private RestaurantReservationRepository reservationRepository;

    @Mock
    private ReservationCallAttemptService callAttemptService;

    @Test
    @DisplayName("provider 표준 이벤트를 예약 상태 변경과 event ledger에 반영한다")
    void processProviderEventAppliesReservationStatus() {
        ReservationProviderEventService service = service();
        RestaurantReservation reservation = reservation();
        reservation.applyProviderStart(
                ReservationStatus.REQUESTED,
                "MOCK",
                "mock-100",
                "MOCK_WAITING_RESULT"
        );

        given(providerEventRepository.findByIdempotencyKey("MOCK:event-1")).willReturn(Optional.empty());
        given(reservationRepository.findByIdForUpdate(100L)).willReturn(Optional.of(reservation));

        ReservationProviderEventProcessResult result = service.processProviderEvent(commandBuilder()
                .providerEventId("event-1")
                .eventType(ReservationProviderEventType.RESERVATION_CONFIRMED)
                .aiSummary("예약 가능 확인")
                .resultMessage("예약이 확정되었습니다.")
                .build());

        assertThat(result.processingStatus()).isEqualTo(ReservationProviderEventProcessingStatus.PROCESSED);
        assertThat(result.statusChanged()).isTrue();
        assertThat(result.reservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getAiSummary()).isEqualTo("예약 가능 확인");

        ArgumentCaptor<ReservationProviderEvent> eventCaptor =
                ArgumentCaptor.forClass(ReservationProviderEvent.class);
        then(providerEventRepository).should().save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getProcessingStatus())
                .isEqualTo(ReservationProviderEventProcessingStatus.PROCESSED);
        assertThat(eventCaptor.getValue().getTargetStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(eventCaptor.getValue().getIdempotencyKey()).isEqualTo("MOCK:event-1");
    }

    @Test
    @DisplayName("같은 idempotency key의 provider 이벤트는 중복 반영하지 않는다")
    void processProviderEventIgnoresDuplicateIdempotencyKey() {
        ReservationProviderEventService service = service();
        RestaurantReservation reservation = reservation();
        ReservationProviderEvent existingEvent = ReservationProviderEvent.received(
                reservation,
                "MOCK",
                "mock-100",
                "event-1",
                "MOCK:event-1",
                ReservationProviderEventType.RESERVATION_CONFIRMED,
                "MOCK_CONFIRMED",
                ReservationStatus.CONFIRMED,
                null,
                null,
                false,
                LocalDateTime.now(),
                LocalDateTime.now(),
                "hash",
                null,
                true,
                "ok",
                "confirmed"
        );
        existingEvent.markProcessed();

        given(providerEventRepository.findByIdempotencyKey("MOCK:event-1"))
                .willReturn(Optional.of(existingEvent));

        ReservationProviderEventProcessResult result = service.processProviderEvent(commandBuilder()
                .providerEventId("event-1")
                .eventType(ReservationProviderEventType.RESERVATION_CONFIRMED)
                .build());

        assertThat(result.processingStatus()).isEqualTo(ReservationProviderEventProcessingStatus.IGNORED_DUPLICATE);
        assertThat(result.statusChanged()).isFalse();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.REQUESTED);

        then(reservationRepository).shouldHaveNoInteractions();
        then(providerEventRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("보안 검증 실패 이벤트는 예약 상태와 event ledger를 변경하지 않는다")
    void processProviderEventRejectsUnverifiedEvent() {
        ReservationProviderEventService service = service();

        ReservationProviderEventProcessResult result = service.processProviderEvent(commandBuilder()
                .providerEventId("event-1")
                .eventType(ReservationProviderEventType.RESERVATION_CONFIRMED)
                .signatureVerified(false)
                .build());

        assertThat(result.processingStatus()).isEqualTo(ReservationProviderEventProcessingStatus.REJECTED_SECURITY);
        assertThat(result.statusChanged()).isFalse();

        then(reservationRepository).shouldHaveNoInteractions();
        then(providerEventRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("terminal 상태 예약에 늦게 도착한 provider 이벤트는 무시한다")
    void processProviderEventIgnoresStaleEventAfterTerminalStatus() {
        ReservationProviderEventService service = service();
        RestaurantReservation reservation = reservation();
        reservation.applyProviderStart(
                ReservationStatus.REQUESTED,
                "MOCK",
                "mock-100",
                "MOCK_WAITING_RESULT"
        );
        reservation.applyMockResult(
                ReservationStatus.CONFIRMED,
                "예약 가능",
                "예약 확정",
                null,
                "mock-100"
        );

        given(providerEventRepository.findByIdempotencyKey("MOCK:event-2")).willReturn(Optional.empty());
        given(reservationRepository.findByIdForUpdate(100L)).willReturn(Optional.of(reservation));

        ReservationProviderEventProcessResult result = service.processProviderEvent(commandBuilder()
                .providerEventId("event-2")
                .eventType(ReservationProviderEventType.CALL_STARTED)
                .providerStatus("CALL_STARTED")
                .build());

        assertThat(result.processingStatus()).isEqualTo(ReservationProviderEventProcessingStatus.IGNORED_STALE);
        assertThat(result.statusChanged()).isFalse();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);

        ArgumentCaptor<ReservationProviderEvent> eventCaptor =
                ArgumentCaptor.forClass(ReservationProviderEvent.class);
        then(providerEventRepository).should().save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getProcessingStatus())
                .isEqualTo(ReservationProviderEventProcessingStatus.IGNORED_STALE);
    }

    @Test
    @DisplayName("CALL_QUEUED dry-run 이벤트는 예약 provider 필드를 오염시키지 않고 ledger에만 기록한다")
    void processQueuedEventRecordsLedgerOnly() {
        ReservationProviderEventService service = service();
        RestaurantReservation reservation = reservation();

        given(providerEventRepository.findByIdempotencyKey("CLAWOPS_SIDECAR:dry-run-event-1"))
                .willReturn(Optional.empty());
        given(reservationRepository.findByIdForUpdate(100L)).willReturn(Optional.of(reservation));

        ReservationProviderEventProcessResult result = service.processProviderEvent(commandBuilder()
                .provider("CLAWOPS_SIDECAR")
                .providerCallId("dry-run-sidecar-100")
                .providerEventId("dry-run-event-1")
                .eventType(ReservationProviderEventType.CALL_QUEUED)
                .providerStatus("DRY_RUN_QUEUED")
                .build());

        assertThat(result.processingStatus()).isEqualTo(ReservationProviderEventProcessingStatus.PROCESSED);
        assertThat(result.statusChanged()).isFalse();
        assertThat(result.reservationStatus()).isEqualTo(ReservationStatus.REQUESTED);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.REQUESTED);
        assertThat(reservation.getProvider()).isNull();
        assertThat(reservation.getProviderCallId()).isNull();
        then(callAttemptService).shouldHaveNoInteractions();

        ArgumentCaptor<ReservationProviderEvent> eventCaptor =
                ArgumentCaptor.forClass(ReservationProviderEvent.class);
        then(providerEventRepository).should().save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getProcessingStatus())
                .isEqualTo(ReservationProviderEventProcessingStatus.PROCESSED);
        assertThat(eventCaptor.getValue().getTargetStatus()).isNull();
    }

    @Test
    @DisplayName("provider 식별자가 예약과 다르면 예약 상태를 오염시키지 않는다")
    void processProviderEventRejectsMismatchedProviderCallId() {
        ReservationProviderEventService service = service();
        RestaurantReservation reservation = reservation();
        reservation.applyProviderStart(
                ReservationStatus.REQUESTED,
                "MOCK",
                "mock-100",
                "MOCK_WAITING_RESULT"
        );

        given(providerEventRepository.findByIdempotencyKey("MOCK:event-3")).willReturn(Optional.empty());
        given(reservationRepository.findByIdForUpdate(100L)).willReturn(Optional.of(reservation));

        ReservationProviderEventProcessResult result = service.processProviderEvent(commandBuilder()
                .providerCallId("other-call")
                .providerEventId("event-3")
                .eventType(ReservationProviderEventType.RESERVATION_CONFIRMED)
                .build());

        assertThat(result.processingStatus()).isEqualTo(ReservationProviderEventProcessingStatus.REJECTED_INVALID);
        assertThat(result.statusChanged()).isFalse();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.REQUESTED);

        ArgumentCaptor<ReservationProviderEvent> eventCaptor =
                ArgumentCaptor.forClass(ReservationProviderEvent.class);
        then(providerEventRepository).should().save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getProcessingStatus())
                .isEqualTo(ReservationProviderEventProcessingStatus.REJECTED_INVALID);
    }

    @Test
    @DisplayName("call attempt 정책이 최종 실패를 반환하면 예약을 FAILED로 전이한다")
    void processProviderEventAppliesFinalFailureFromCallAttemptPolicy() {
        ReservationProviderEventService service = service();
        RestaurantReservation reservation = reservation();
        reservation.applyProviderStart(
                ReservationStatus.CALLING,
                "MOCK",
                "mock-100",
                "MOCK_CALLING"
        );
        ReservationProviderEventCommand command = commandBuilder()
                .providerEventId("event-final-failure")
                .eventType(ReservationProviderEventType.CALL_BUSY)
                .retryable(true)
                .failureCode("BUSY")
                .failureReason("통화 중")
                .build();

        given(providerEventRepository.findByIdempotencyKey("MOCK:event-final-failure"))
                .willReturn(Optional.empty());
        given(reservationRepository.findByIdForUpdate(100L)).willReturn(Optional.of(reservation));
        given(callAttemptService.recordProviderEvent(reservation, command))
                .willReturn(com.example.Capstone.client.reservation.ReservationCallAttemptProcessResult.of(
                        com.example.Capstone.domain.ReservationCallAttemptStatus.FAILED_FINAL,
                        false,
                        true,
                        null,
                        ReservationStatus.FAILED,
                        "최대 재시도 횟수를 초과했습니다."
                ));

        ReservationProviderEventProcessResult result = service.processProviderEvent(command);

        assertThat(result.processingStatus()).isEqualTo(ReservationProviderEventProcessingStatus.PROCESSED);
        assertThat(result.reservationStatus()).isEqualTo(ReservationStatus.FAILED);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.FAILED);
        assertThat(reservation.getFailureReason()).isEqualTo("최대 재시도 횟수를 초과했습니다.");
    }

    private ReservationProviderEventService service() {
        return new ReservationProviderEventService(
                providerEventRepository,
                reservationRepository,
                new ReservationProviderEventIdempotencyKeyFactory(),
                callAttemptService
        );
    }

    private ReservationProviderEventCommand.ReservationProviderEventCommandBuilder commandBuilder() {
        return ReservationProviderEventCommand.builder()
                .provider("MOCK")
                .providerCallId("mock-100")
                .reservationId(100L)
                .providerStatus("MOCK_EVENT")
                .occurredAt(LocalDateTime.now())
                .rawPayloadHash("payload-hash")
                .signatureVerified(true);
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
