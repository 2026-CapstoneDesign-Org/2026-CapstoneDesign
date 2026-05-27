package com.example.Capstone.client.reservation;

import java.time.LocalDateTime;

import com.example.Capstone.domain.ReservationCallAttemptStatus;
import com.example.Capstone.domain.ReservationStatus;

public record ReservationCallAttemptProcessResult(
        ReservationCallAttemptStatus attemptStatus,
        boolean retryScheduled,
        boolean finalFailure,
        LocalDateTime nextRetryAt,
        ReservationStatus targetStatusOverride,
        String failureReasonOverride
) {
    public static ReservationCallAttemptProcessResult noChange() {
        return new ReservationCallAttemptProcessResult(null, false, false, null, null, null);
    }

    public static ReservationCallAttemptProcessResult of(
            ReservationCallAttemptStatus attemptStatus,
            boolean retryScheduled,
            boolean finalFailure,
            LocalDateTime nextRetryAt,
            ReservationStatus targetStatusOverride,
            String failureReasonOverride
    ) {
        return new ReservationCallAttemptProcessResult(
                attemptStatus,
                retryScheduled,
                finalFailure,
                nextRetryAt,
                targetStatusOverride,
                failureReasonOverride
        );
    }
}
