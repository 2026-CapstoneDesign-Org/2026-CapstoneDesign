package com.example.Capstone.client.reservation;

public record ClawOpsSidecarReadinessCheckResult(
        boolean ready,
        String blockReason,
        String safeMessage,
        ClawOpsSidecarReadinessResponse response
) {
    public static ClawOpsSidecarReadinessCheckResult ready(ClawOpsSidecarReadinessResponse response) {
        return new ClawOpsSidecarReadinessCheckResult(true, null, null, response);
    }

    public static ClawOpsSidecarReadinessCheckResult blocked(String blockReason, String safeMessage) {
        return new ClawOpsSidecarReadinessCheckResult(false, blockReason, safeMessage, null);
    }
}
