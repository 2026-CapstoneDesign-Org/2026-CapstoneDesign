package com.example.Capstone.client.reservation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

@Component
public class ReservationProviderEventIdempotencyKeyFactory {

    private static final ZoneId RESERVATION_ZONE = ZoneId.of("Asia/Seoul");

    public String create(ReservationProviderEventCommand command) {
        String provider = requireText(command.getProvider(), "provider는 필수입니다.");
        if (hasText(command.getProviderEventId())) {
            return provider + ":" + command.getProviderEventId().trim();
        }

        String providerCallId = requireText(command.getProviderCallId(), "providerCallId는 필수입니다.");
        if (command.getEventType() == null) {
            throw new IllegalArgumentException("eventType은 필수입니다.");
        }
        if (command.getOccurredAt() == null) {
            throw new IllegalArgumentException("occurredAt은 필수입니다.");
        }
        String rawPayloadHash = rawPayloadHash(command);
        long occurredAtEpochMillis = command.getOccurredAt()
                .atZone(RESERVATION_ZONE)
                .toInstant()
                .toEpochMilli();

        return provider
                + ":" + providerCallId.trim()
                + ":" + command.getEventType().name()
                + ":" + occurredAtEpochMillis
                + ":" + rawPayloadHash;
    }

    public String rawPayloadHash(ReservationProviderEventCommand command) {
        if (hasText(command.getRawPayloadHash())) {
            return command.getRawPayloadHash().trim();
        }
        return sha256(command.getRawPayload() == null ? "" : command.getRawPayload());
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(encoded.length * 2);
            for (byte b : encoded) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available.", exception);
        }
    }

    private String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
