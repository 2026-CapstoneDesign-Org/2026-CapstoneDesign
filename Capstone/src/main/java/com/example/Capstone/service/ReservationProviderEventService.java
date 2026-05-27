package com.example.Capstone.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Capstone.client.reservation.ReservationCallAttemptProcessResult;
import com.example.Capstone.client.reservation.ReservationProviderEventCommand;
import com.example.Capstone.client.reservation.ReservationProviderEventIdempotencyKeyFactory;
import com.example.Capstone.client.reservation.ReservationProviderEventProcessResult;
import com.example.Capstone.domain.ReservationProviderEvent;
import com.example.Capstone.domain.ReservationProviderEventProcessingStatus;
import com.example.Capstone.domain.ReservationProviderEventType;
import com.example.Capstone.domain.ReservationStatus;
import com.example.Capstone.domain.RestaurantReservation;
import com.example.Capstone.repository.ReservationProviderEventRepository;
import com.example.Capstone.repository.RestaurantReservationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationProviderEventService {

    private final ReservationProviderEventRepository providerEventRepository;
    private final RestaurantReservationRepository reservationRepository;
    private final ReservationProviderEventIdempotencyKeyFactory idempotencyKeyFactory;
    private final ReservationCallAttemptService callAttemptService;

    @Transactional
    public ReservationProviderEventProcessResult processProviderEvent(ReservationProviderEventCommand command) {
        if (command == null || !command.isSignatureVerified()) {
            return ReservationProviderEventProcessResult.of(
                    ReservationProviderEventProcessingStatus.REJECTED_SECURITY,
                    false,
                    command == null ? null : command.getReservationId(),
                    null,
                    "provider event security 검증에 실패했습니다."
            );
        }

        String idempotencyKey;
        String rawPayloadHash;
        try {
            idempotencyKey = idempotencyKeyFactory.create(command);
            rawPayloadHash = idempotencyKeyFactory.rawPayloadHash(command);
        } catch (IllegalArgumentException exception) {
            return ReservationProviderEventProcessResult.of(
                    ReservationProviderEventProcessingStatus.REJECTED_INVALID,
                    false,
                    command.getReservationId(),
                    null,
                    exception.getMessage()
            );
        }

        Optional<ReservationProviderEvent> duplicateEvent =
                providerEventRepository.findByIdempotencyKey(idempotencyKey);
        if (duplicateEvent.isPresent()) {
            return duplicateResult(duplicateEvent.get());
        }

        Optional<RestaurantReservation> maybeReservation = resolveReservation(command);
        ReservationStatus targetStatus = command.resolvedTargetStatus();
        ReservationProviderEvent event = ReservationProviderEvent.received(
                maybeReservation.orElse(null),
                command.getProvider(),
                command.getProviderCallId(),
                command.getProviderEventId(),
                idempotencyKey,
                command.getEventType(),
                command.getProviderStatus(),
                targetStatus,
                command.getFailureCode(),
                command.getFailureReason(),
                command.isRetryable(),
                command.getOccurredAt(),
                LocalDateTime.now(),
                rawPayloadHash,
                command.getRawPayload(),
                true,
                command.getAiSummary(),
                command.getResultMessage()
        );

        if (maybeReservation.isEmpty()) {
            event.markRejectedInvalid("예약을 찾을 수 없습니다.");
            providerEventRepository.save(event);
            return ReservationProviderEventProcessResult.of(
                    event.getProcessingStatus(),
                    false,
                    command.getReservationId(),
                    null,
                    event.getFailureReason()
            );
        }

        RestaurantReservation reservation = maybeReservation.get();
        if (!matchesExistingProviderCall(reservation, command)) {
            event.markRejectedInvalid("provider 식별자가 예약과 일치하지 않습니다.");
            providerEventRepository.save(event);
            return ReservationProviderEventProcessResult.of(
                    event.getProcessingStatus(),
                    false,
                    reservation.getId(),
                    reservation.getStatus(),
                    event.getFailureReason()
            );
        }
        if (targetStatus == ReservationStatus.REQUESTED || targetStatus == ReservationStatus.CANCELED) {
            event.markRejectedInvalid("provider event로 반영할 수 없는 예약 상태입니다.");
            providerEventRepository.save(event);
            return ReservationProviderEventProcessResult.of(
                    event.getProcessingStatus(),
                    false,
                    reservation.getId(),
                    reservation.getStatus(),
                    event.getFailureReason()
            );
        }
        if (targetStatus == null && command.getEventType() == ReservationProviderEventType.CALL_QUEUED) {
            event.markProcessed();
            providerEventRepository.save(event);
            return ReservationProviderEventProcessResult.of(
                    event.getProcessingStatus(),
                    false,
                    reservation.getId(),
                    reservation.getStatus(),
                    "provider queued event를 ledger에 기록했습니다."
            );
        }
        if (reservation.getStatus().isTerminal()) {
            event.markIgnoredStale();
            providerEventRepository.save(event);
            return ReservationProviderEventProcessResult.of(
                    event.getProcessingStatus(),
                    false,
                    reservation.getId(),
                    reservation.getStatus(),
                "terminal 예약에는 provider event를 다시 반영하지 않습니다."
            );
        }

        ReservationStatus previousStatus = reservation.getStatus();
        ReservationCallAttemptProcessResult attemptResult =
                callAttemptService.recordProviderEvent(reservation, command);
        if (attemptResult != null && attemptResult.targetStatusOverride() != null) {
            targetStatus = attemptResult.targetStatusOverride();
        }
        String failureReason = command.getFailureReason();
        if (attemptResult != null && attemptResult.failureReasonOverride() != null) {
            failureReason = attemptResult.failureReasonOverride();
        }
        event.overrideTarget(targetStatus, failureReason);
        try {
            reservation.applyProviderEvent(
                    targetStatus,
                    command.getProvider(),
                    command.getProviderCallId(),
                    command.getProviderStatus(),
                    command.getAiSummary(),
                    command.getResultMessage(),
                    failureReason
            );
            event.markProcessed();
            providerEventRepository.save(event);
            return ReservationProviderEventProcessResult.of(
                    event.getProcessingStatus(),
                    previousStatus != reservation.getStatus(),
                    reservation.getId(),
                    reservation.getStatus(),
                    "provider event를 처리했습니다."
            );
        } catch (IllegalStateException exception) {
            event.markRejectedInvalid(exception.getMessage());
            providerEventRepository.save(event);
            return ReservationProviderEventProcessResult.of(
                    event.getProcessingStatus(),
                    false,
                    reservation.getId(),
                    reservation.getStatus(),
                    exception.getMessage()
            );
        }
    }

    private Optional<RestaurantReservation> resolveReservation(ReservationProviderEventCommand command) {
        if (command.getReservationId() != null) {
            return reservationRepository.findByIdForUpdate(command.getReservationId());
        }
        if (hasText(command.getProvider()) && hasText(command.getProviderCallId())) {
            return reservationRepository.findByProviderAndProviderCallId(
                    command.getProvider().trim(),
                    command.getProviderCallId().trim()
            );
        }
        return Optional.empty();
    }

    private ReservationProviderEventProcessResult duplicateResult(ReservationProviderEvent event) {
        RestaurantReservation reservation = event.getReservation();
        return ReservationProviderEventProcessResult.of(
                ReservationProviderEventProcessingStatus.IGNORED_DUPLICATE,
                false,
                reservation == null ? null : reservation.getId(),
                reservation == null ? null : reservation.getStatus(),
                "이미 처리된 provider event입니다."
        );
    }

    private boolean matchesExistingProviderCall(
            RestaurantReservation reservation,
            ReservationProviderEventCommand command
    ) {
        return matchesIfBothPresent(reservation.getProvider(), command.getProvider())
                && matchesIfBothPresent(reservation.getProviderCallId(), command.getProviderCallId());
    }

    private boolean matchesIfBothPresent(String existingValue, String eventValue) {
        if (!hasText(existingValue) || !hasText(eventValue)) {
            return true;
        }
        return existingValue.trim().equals(eventValue.trim());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
