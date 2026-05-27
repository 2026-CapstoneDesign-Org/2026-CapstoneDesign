package com.example.Capstone.domain;

import java.util.EnumSet;
import java.util.Set;

public enum ReservationStatus {
    REQUESTED,
    CALLING,
    CONFIRMED,
    UNAVAILABLE,
    NEEDS_CONFIRMATION,
    FAILED,
    CANCELED;

    private static final Set<ReservationStatus> DUPLICATE_BLOCKING_STATUSES = EnumSet.of(
            REQUESTED,
            CALLING,
            CONFIRMED,
            NEEDS_CONFIRMATION
    );

    public boolean canTransitionTo(ReservationStatus nextStatus) {
        if (nextStatus == null || this == nextStatus) {
            return false;
        }

        return switch (this) {
            case REQUESTED -> nextStatus == CALLING
                    || nextStatus == CONFIRMED
                    || nextStatus == UNAVAILABLE
                    || nextStatus == NEEDS_CONFIRMATION
                    || nextStatus == FAILED
                    || nextStatus == CANCELED;
            case CALLING -> nextStatus == CONFIRMED
                    || nextStatus == UNAVAILABLE
                    || nextStatus == NEEDS_CONFIRMATION
                    || nextStatus == FAILED;
            case NEEDS_CONFIRMATION -> nextStatus == CANCELED;
            case CONFIRMED, UNAVAILABLE, FAILED, CANCELED -> false;
        };
    }

    public boolean canApplyMockResult(ReservationStatus nextStatus) {
        return nextStatus != REQUESTED
                && nextStatus != CANCELED
                && canTransitionTo(nextStatus);
    }

    public boolean isCancelable() {
        return this == REQUESTED || this == NEEDS_CONFIRMATION;
    }

    public boolean isTerminal() {
        return this == CONFIRMED || this == UNAVAILABLE || this == FAILED || this == CANCELED;
    }

    public static Set<ReservationStatus> duplicateBlockingStatuses() {
        return Set.copyOf(DUPLICATE_BLOCKING_STATUSES);
    }
}
