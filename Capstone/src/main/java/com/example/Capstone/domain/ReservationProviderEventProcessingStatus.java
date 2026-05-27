package com.example.Capstone.domain;

public enum ReservationProviderEventProcessingStatus {
    RECEIVED,
    PROCESSED,
    IGNORED_DUPLICATE,
    IGNORED_STALE,
    REJECTED_SECURITY,
    REJECTED_INVALID
}
