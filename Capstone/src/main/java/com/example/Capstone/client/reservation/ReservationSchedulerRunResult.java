package com.example.Capstone.client.reservation;

public record ReservationSchedulerRunResult(
        int retryCandidates,
        int retriesDispatched,
        int timeoutCandidates,
        int timeoutsScheduled,
        int timeoutsFailed,
        int skipped
) {
    public static ReservationSchedulerRunResult empty() {
        return new ReservationSchedulerRunResult(0, 0, 0, 0, 0, 0);
    }

    public ReservationSchedulerRunResult plus(ReservationSchedulerRunResult other) {
        if (other == null) {
            return this;
        }
        return new ReservationSchedulerRunResult(
                retryCandidates + other.retryCandidates,
                retriesDispatched + other.retriesDispatched,
                timeoutCandidates + other.timeoutCandidates,
                timeoutsScheduled + other.timeoutsScheduled,
                timeoutsFailed + other.timeoutsFailed,
                skipped + other.skipped
        );
    }
}
