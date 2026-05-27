package com.example.Capstone.client.reservation;

public record ClawOpsSidecarCallRequest(
        Long reservationId,
        Long callAttemptId,
        String idempotencyKey,
        String targetPhoneNumber,
        String restaurantName,
        String reservationDateTime,
        Integer partySize,
        String requestNote,
        Boolean dryRun,
        Boolean springPreflightPassed
) {
    public static ClawOpsSidecarCallRequest from(PhoneProviderCallStartCommand command, boolean dryRun) {
        if (command == null) {
            throw new IllegalArgumentException("sidecar call command는 필수입니다.");
        }
        return new ClawOpsSidecarCallRequest(
                command.reservationId(),
                null,
                (dryRun ? "clawops-sidecar-dry-run:" : "clawops-sidecar-real-agent:") + command.reservationId(),
                command.toPhoneNumber(),
                command.restaurantName(),
                command.reservationDateTime() == null ? null : command.reservationDateTime().toString(),
                command.partySize(),
                command.requestNote(),
                dryRun,
                !dryRun
        );
    }
}
