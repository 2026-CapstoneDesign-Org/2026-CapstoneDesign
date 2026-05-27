package com.example.Capstone.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Capstone.client.reservation.ReservationCallAttemptProcessResult;
import com.example.Capstone.client.reservation.ReservationProviderEventCommand;
import com.example.Capstone.client.reservation.ReservationRetryPolicy;
import com.example.Capstone.domain.ReservationCallAttempt;
import com.example.Capstone.domain.ReservationCallAttemptStatus;
import com.example.Capstone.domain.ReservationProviderEventType;
import com.example.Capstone.domain.ReservationStatus;
import com.example.Capstone.domain.RestaurantReservation;
import com.example.Capstone.repository.ReservationCallAttemptRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationCallAttemptService {

    private static final String SCHEDULER_PROVIDER_EVENT_PREFIX = "scheduler";
    private static final String TIMEOUT_FAILURE_CODE = "PROVIDER_TIMEOUT";
    private static final String TIMEOUT_FAILURE_REASON = "전화 provider 응답 시간이 초과되었습니다.";

    private static final Set<ReservationCallAttemptStatus> TIMEOUT_CANDIDATE_STATUSES = Set.of(
            ReservationCallAttemptStatus.STARTED,
            ReservationCallAttemptStatus.CONNECTED
    );

    private final ReservationCallAttemptRepository callAttemptRepository;
    private final ReservationRetryPolicy retryPolicy;

    @Transactional
    public ReservationCallAttemptProcessResult recordProviderEvent(
            RestaurantReservation reservation,
            ReservationProviderEventCommand command
    ) {
        if (reservation == null || command == null || command.getEventType() == null) {
            return ReservationCallAttemptProcessResult.noChange();
        }
        if (!hasText(command.getProvider()) || !hasText(command.getProviderCallId())) {
            return ReservationCallAttemptProcessResult.noChange();
        }

        return switch (command.getEventType()) {
            case CALL_STARTED -> recordStarted(reservation, command);
            case CALL_CONNECTED -> recordConnected(reservation, command);
            case CALL_CONNECTION_FAILED, CALL_NO_ANSWER, CALL_BUSY, PROVIDER_TRANSIENT_ERROR ->
                    recordFailure(reservation, command);
            case PROVIDER_FATAL_ERROR -> recordFinalFailure(reservation, command);
            case RESERVATION_CONFIRMED, RESERVATION_UNAVAILABLE, RESERVATION_NEEDS_CONFIRMATION,
                    AI_PARSE_FAILED, CALL_ENDED -> recordCompleted(reservation, command);
            case CALL_QUEUED -> ReservationCallAttemptProcessResult.noChange();
        };
    }

    public List<ReservationCallAttempt> findRetryReadyAttempts(LocalDateTime referenceTime) {
        return callAttemptRepository.findByStatusAndNextRetryAtLessThanEqual(
                ReservationCallAttemptStatus.RETRY_SCHEDULED,
                referenceTime == null ? LocalDateTime.now() : referenceTime
        );
    }

    public List<ReservationCallAttempt> findTimeoutCandidates(LocalDateTime referenceTime) {
        return callAttemptRepository.findByStatusInAndStartedAtBefore(
                TIMEOUT_CANDIDATE_STATUSES,
                retryPolicy.timeoutThreshold(referenceTime)
        );
    }

    @Transactional
    public ReservationCallAttemptProcessResult startMockRetryAttempt(
            ReservationCallAttempt retryAttempt,
            LocalDateTime startedAt
    ) {
        if (retryAttempt == null || !retryAttempt.isRetryReady()) {
            return ReservationCallAttemptProcessResult.noChange();
        }

        RestaurantReservation reservation = retryAttempt.getReservation();
        if (reservation == null
                || reservation.getStatus().isTerminal()
                || reservation.getStatus() == ReservationStatus.NEEDS_CONFIRMATION) {
            return ReservationCallAttemptProcessResult.noChange();
        }

        int nextAttemptNumber = callAttemptRepository.findTopByReservationIdOrderByAttemptNumberDesc(
                        reservation.getId()
                )
                .map(attempt -> attempt.getAttemptNumber() + 1)
                .orElse(retryAttempt.getAttemptNumber() + 1);
        String retryProviderCallId = "mock-retry-" + reservation.getId() + "-" + nextAttemptNumber;
        LocalDateTime retryStartedAt = startedAt == null ? LocalDateTime.now() : startedAt;

        retryAttempt.markRetryDispatched(SCHEDULER_PROVIDER_EVENT_PREFIX + "-retry-" + retryProviderCallId);
        reservation.recordCallAttemptStarted(
                retryAttempt.getProvider(),
                retryProviderCallId,
                "MOCK_RETRY_DISPATCHED",
                retryStartedAt
        );

        callAttemptRepository.save(ReservationCallAttempt.started(
                reservation,
                nextAttemptNumber,
                retryAttempt.getProvider(),
                retryProviderCallId,
                retryStartedAt,
                SCHEDULER_PROVIDER_EVENT_PREFIX + "-retry-started-" + retryProviderCallId
        ));

        return ReservationCallAttemptProcessResult.of(
                ReservationCallAttemptStatus.STARTED,
                false,
                false,
                null,
                null,
                null
        );
    }

    @Transactional
    public ReservationCallAttemptProcessResult handleTimeout(
            ReservationCallAttempt timeoutAttempt,
            LocalDateTime timedOutAt
    ) {
        if (timeoutAttempt == null || !timeoutAttempt.isTimeoutCandidateStatus()) {
            return ReservationCallAttemptProcessResult.noChange();
        }

        RestaurantReservation reservation = timeoutAttempt.getReservation();
        if (reservation == null
                || reservation.getStatus().isTerminal()
                || reservation.getStatus() == ReservationStatus.NEEDS_CONFIRMATION) {
            return ReservationCallAttemptProcessResult.noChange();
        }

        LocalDateTime failureTime = timedOutAt == null ? LocalDateTime.now() : timedOutAt;
        if (retryPolicy.canRetryAfterAttempt(timeoutAttempt.getAttemptNumber())) {
            LocalDateTime nextRetryAt = retryPolicy.nextRetryAt(timeoutAttempt.getAttemptNumber(), failureTime);
            timeoutAttempt.markRetryScheduled(
                    TIMEOUT_FAILURE_CODE,
                    TIMEOUT_FAILURE_REASON,
                    nextRetryAt,
                    SCHEDULER_PROVIDER_EVENT_PREFIX + "-timeout-" + timeoutAttempt.getId()
            );
            return ReservationCallAttemptProcessResult.of(
                    timeoutAttempt.getStatus(),
                    true,
                    false,
                    nextRetryAt,
                    null,
                    null
            );
        }

        timeoutAttempt.markFinalFailure(
                TIMEOUT_FAILURE_CODE,
                TIMEOUT_FAILURE_REASON,
                SCHEDULER_PROVIDER_EVENT_PREFIX + "-timeout-final-" + timeoutAttempt.getId()
        );
        reservation.applyProviderEvent(
                ReservationStatus.FAILED,
                timeoutAttempt.getProvider(),
                timeoutAttempt.getProviderCallId(),
                "MOCK_TIMEOUT_FAILED",
                null,
                null,
                TIMEOUT_FAILURE_REASON
        );
        return ReservationCallAttemptProcessResult.of(
                timeoutAttempt.getStatus(),
                false,
                true,
                null,
                ReservationStatus.FAILED,
                TIMEOUT_FAILURE_REASON
        );
    }

    private ReservationCallAttemptProcessResult recordStarted(
            RestaurantReservation reservation,
            ReservationProviderEventCommand command
    ) {
        ReservationCallAttempt attempt = findOrCreateAttempt(reservation, command);
        return ReservationCallAttemptProcessResult.of(
                attempt.getStatus(),
                false,
                false,
                null,
                null,
                null
        );
    }

    private ReservationCallAttemptProcessResult recordConnected(
            RestaurantReservation reservation,
            ReservationProviderEventCommand command
    ) {
        ReservationCallAttempt attempt = findOrCreateAttempt(reservation, command);
        attempt.markConnected(command.getProviderEventId());
        return ReservationCallAttemptProcessResult.of(
                attempt.getStatus(),
                false,
                false,
                null,
                null,
                null
        );
    }

    private ReservationCallAttemptProcessResult recordFailure(
            RestaurantReservation reservation,
            ReservationProviderEventCommand command
    ) {
        ReservationCallAttempt attempt = findOrCreateAttempt(reservation, command);
        LocalDateTime failedAt = command.getOccurredAt() == null ? LocalDateTime.now() : command.getOccurredAt();
        if (command.isRetryable() && retryPolicy.canRetryAfterAttempt(attempt.getAttemptNumber())) {
            LocalDateTime nextRetryAt = retryPolicy.nextRetryAt(attempt.getAttemptNumber(), failedAt);
            attempt.markRetryScheduled(
                    command.getFailureCode(),
                    command.getFailureReason(),
                    nextRetryAt,
                    command.getProviderEventId()
            );
            return ReservationCallAttemptProcessResult.of(
                    attempt.getStatus(),
                    true,
                    false,
                    nextRetryAt,
                    null,
                    null
            );
        }

        return markFinalFailure(attempt, command, "최대 재시도 횟수를 초과했습니다.");
    }

    private ReservationCallAttemptProcessResult recordFinalFailure(
            RestaurantReservation reservation,
            ReservationProviderEventCommand command
    ) {
        return markFinalFailure(
                findOrCreateAttempt(reservation, command),
                command,
                command.getFailureReason()
        );
    }

    private ReservationCallAttemptProcessResult markFinalFailure(
            ReservationCallAttempt attempt,
            ReservationProviderEventCommand command,
            String defaultFailureReason
    ) {
        String failureReason = hasText(command.getFailureReason())
                ? command.getFailureReason()
                : defaultFailureReason;
        attempt.markFinalFailure(
                command.getFailureCode(),
                failureReason,
                command.getProviderEventId()
        );
        return ReservationCallAttemptProcessResult.of(
                attempt.getStatus(),
                false,
                true,
                null,
                ReservationStatus.FAILED,
                failureReason
        );
    }

    private ReservationCallAttemptProcessResult recordCompleted(
            RestaurantReservation reservation,
            ReservationProviderEventCommand command
    ) {
        ReservationCallAttempt attempt = findOrCreateAttempt(reservation, command);
        attempt.markCompleted(command.getProviderEventId());
        return ReservationCallAttemptProcessResult.of(
                attempt.getStatus(),
                false,
                false,
                null,
                null,
                null
        );
    }

    private ReservationCallAttempt findOrCreateAttempt(
            RestaurantReservation reservation,
            ReservationProviderEventCommand command
    ) {
        Optional<ReservationCallAttempt> existingAttempt =
                callAttemptRepository.findByReservationIdAndProviderAndProviderCallId(
                        reservation.getId(),
                        command.getProvider(),
                        command.getProviderCallId()
                );
        if (existingAttempt.isPresent()) {
            return existingAttempt.get();
        }

        int nextAttemptNumber = callAttemptRepository.findTopByReservationIdOrderByAttemptNumberDesc(
                        reservation.getId()
                )
                .map(attempt -> attempt.getAttemptNumber() + 1)
                .orElse(1);
        reservation.recordCallAttemptStarted(
                command.getProvider(),
                command.getProviderCallId(),
                command.getProviderStatus(),
                command.getOccurredAt()
        );
        return callAttemptRepository.save(ReservationCallAttempt.started(
                reservation,
                nextAttemptNumber,
                command.getProvider(),
                command.getProviderCallId(),
                command.getOccurredAt(),
                command.getProviderEventId()
        ));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
