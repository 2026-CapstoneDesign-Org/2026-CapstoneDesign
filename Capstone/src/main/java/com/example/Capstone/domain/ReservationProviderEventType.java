package com.example.Capstone.domain;

public enum ReservationProviderEventType {
    CALL_QUEUED,
    CALL_STARTED,
    CALL_CONNECTED,
    CALL_CONNECTION_FAILED,
    CALL_NO_ANSWER,
    CALL_BUSY,
    CALL_ENDED,
    RESERVATION_CONFIRMED,
    RESERVATION_UNAVAILABLE,
    RESERVATION_NEEDS_CONFIRMATION,
    AI_PARSE_FAILED,
    PROVIDER_TRANSIENT_ERROR,
    PROVIDER_FATAL_ERROR;

    public ReservationStatus defaultTargetStatus(boolean retryable) {
        return switch (this) {
            case CALL_STARTED -> ReservationStatus.CALLING;
            case RESERVATION_CONFIRMED -> ReservationStatus.CONFIRMED;
            case RESERVATION_UNAVAILABLE -> ReservationStatus.UNAVAILABLE;
            case RESERVATION_NEEDS_CONFIRMATION, AI_PARSE_FAILED -> ReservationStatus.NEEDS_CONFIRMATION;
            case PROVIDER_FATAL_ERROR -> ReservationStatus.FAILED;
            case CALL_CONNECTION_FAILED, CALL_NO_ANSWER, CALL_BUSY, PROVIDER_TRANSIENT_ERROR -> retryable
                    ? null
                    : ReservationStatus.FAILED;
            case CALL_QUEUED, CALL_CONNECTED, CALL_ENDED -> null;
        };
    }
}
