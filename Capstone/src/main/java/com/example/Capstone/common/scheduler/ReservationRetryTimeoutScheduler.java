package com.example.Capstone.common.scheduler;

import java.time.LocalDateTime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.Capstone.client.reservation.ReservationSchedulerRunResult;
import com.example.Capstone.service.ReservationRetryTimeoutSchedulerService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "reservation.scheduler.enabled", havingValue = "true")
public class ReservationRetryTimeoutScheduler {

    private final ReservationRetryTimeoutSchedulerService schedulerService;

    @Scheduled(fixedDelayString = "${reservation.scheduler.fixed-delay-ms:60000}")
    public void processDueRetriesAndTimeouts() {
        ReservationSchedulerRunResult result =
                schedulerService.processDueRetriesAndTimeouts(LocalDateTime.now());
        log.info(
                "예약 retry/timeout scheduler 완료: retryCandidates={}, retriesDispatched={}, "
                        + "timeoutCandidates={}, timeoutsScheduled={}, timeoutsFailed={}, skipped={}",
                result.retryCandidates(),
                result.retriesDispatched(),
                result.timeoutCandidates(),
                result.timeoutsScheduled(),
                result.timeoutsFailed(),
                result.skipped()
        );
    }
}
