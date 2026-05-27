package com.example.Capstone.domain;

public enum ReservationCallAttemptStatus {
    STARTED,
    CONNECTED,
    RETRY_SCHEDULED,
    RETRY_DISPATCHED,
    COMPLETED,
    FAILED_FINAL
}
