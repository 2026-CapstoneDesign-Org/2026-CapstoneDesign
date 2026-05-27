package com.example.Capstone.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Capstone.client.reservation.ReservationCallAttemptProcessResult;
import com.example.Capstone.client.reservation.ReservationSchedulerRunResult;
import com.example.Capstone.common.scheduler.ReservationSchedulerExecutionGuard;
import com.example.Capstone.domain.ReservationCallAttempt;
import com.example.Capstone.domain.ReservationStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationRetryTimeoutSchedulerService {

    private final ReservationCallAttemptService callAttemptService;
    private final ReservationSchedulerExecutionGuard executionGuard;

    @Transactional
    public ReservationSchedulerRunResult processDueRetriesAndTimeouts(LocalDateTime referenceTime) {
        LocalDateTime now = referenceTime == null ? LocalDateTime.now() : referenceTime;
        return processRetryReadyAttempts(now).plus(processTimeoutCandidates(now));
    }

    @Transactional
    public ReservationSchedulerRunResult processRetryReadyAttempts(LocalDateTime referenceTime) {
        LocalDateTime now = referenceTime == null ? LocalDateTime.now() : referenceTime;
        List<ReservationCallAttempt> candidates = callAttemptService.findRetryReadyAttempts(now);

        int dispatched = 0;
        int skipped = 0;
        for (ReservationCallAttempt attempt : candidates) {
            String guardKey = guardKey("retry", attempt);
            if (shouldSkip(attempt) || !executionGuard.tryStart(guardKey)) {
                skipped += 1;
                continue;
            }

            try {
                ReservationCallAttemptProcessResult result =
                        callAttemptService.startMockRetryAttempt(attempt, now);
                if (result.attemptStatus() == null) {
                    skipped += 1;
                } else {
                    dispatched += 1;
                }
            } finally {
                executionGuard.finish(guardKey);
            }
        }

        return new ReservationSchedulerRunResult(candidates.size(), dispatched, 0, 0, 0, skipped);
    }

    @Transactional
    public ReservationSchedulerRunResult processTimeoutCandidates(LocalDateTime referenceTime) {
        LocalDateTime now = referenceTime == null ? LocalDateTime.now() : referenceTime;
        List<ReservationCallAttempt> candidates = callAttemptService.findTimeoutCandidates(now);

        int scheduled = 0;
        int failed = 0;
        int skipped = 0;
        for (ReservationCallAttempt attempt : candidates) {
            String guardKey = guardKey("timeout", attempt);
            if (shouldSkip(attempt) || !executionGuard.tryStart(guardKey)) {
                skipped += 1;
                continue;
            }

            try {
                ReservationCallAttemptProcessResult result =
                        callAttemptService.handleTimeout(attempt, now);
                if (result.retryScheduled()) {
                    scheduled += 1;
                } else if (result.finalFailure()) {
                    failed += 1;
                } else {
                    skipped += 1;
                }
            } finally {
                executionGuard.finish(guardKey);
            }
        }

        return new ReservationSchedulerRunResult(0, 0, candidates.size(), scheduled, failed, skipped);
    }

    private boolean shouldSkip(ReservationCallAttempt attempt) {
        if (attempt == null || attempt.getReservation() == null) {
            return true;
        }

        ReservationStatus reservationStatus = attempt.getReservation().getStatus();
        return reservationStatus.isTerminal() || reservationStatus == ReservationStatus.NEEDS_CONFIRMATION;
    }

    private String guardKey(String type, ReservationCallAttempt attempt) {
        if (attempt == null || attempt.getId() == null) {
            return null;
        }
        return "reservation-call-attempt:" + type + ":" + attempt.getId();
    }
}
