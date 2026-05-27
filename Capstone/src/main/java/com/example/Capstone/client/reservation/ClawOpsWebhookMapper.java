package com.example.Capstone.client.reservation;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.Capstone.domain.ReservationProviderEventType;

@Component
public class ClawOpsWebhookMapper {

    private static final String PROVIDER = "CLAWOPS";

    public ReservationProviderEventCommand toCommand(String rawPayload) {
        return toCommand(ClawOpsWebhookFormParser.parse(rawPayload), rawPayload);
    }

    public ReservationProviderEventCommand toCommand(Map<String, String> parameters, String rawPayload) {
        if (parameters == null || parameters.isEmpty()) {
            throw new IllegalArgumentException("ClawOps webhook payload가 없습니다.");
        }

        String providerCallId = requireText(parameter(parameters, "CallId"), "CallId는 필수입니다.");
        String providerStatus = firstText(parameter(parameters, "CallStatus"), parameter(parameters, "Event"));
        if (!hasText(providerStatus)) {
            throw new IllegalArgumentException("CallStatus 또는 Event는 필수입니다.");
        }

        ReservationProviderEventType eventType = toEventType(providerStatus);
        return ReservationProviderEventCommand.builder()
                .provider(PROVIDER)
                .providerCallId(providerCallId)
                .providerEventId(firstText(
                        parameter(parameters, "EventId"),
                        parameter(parameters, "WebhookEventId"),
                        parameter(parameters, "DeliveryId")
                ))
                .reservationId(parseLong(firstText(
                        parameter(parameters, "ReservationId"),
                        parameter(parameters, "reservationId")
                )))
                .eventType(eventType)
                .providerStatus(providerStatus)
                .occurredAt(parseTimestamp(parameter(parameters, "Timestamp")))
                .failureCode(firstText(parameter(parameters, "ErrorCode"), parameter(parameters, "Stage")))
                .failureReason(firstText(parameter(parameters, "ErrorMessage"), parameter(parameters, "HangupCause")))
                .retryable(isRetryable(eventType))
                .rawPayload(rawPayload)
                .signatureVerified(true)
                .build();
    }

    private ReservationProviderEventType toEventType(String providerStatus) {
        String normalized = providerStatus.trim().toLowerCase(Locale.ROOT)
                .replace('_', '-');
        return switch (normalized) {
            case "initiated", "ringing" -> ReservationProviderEventType.CALL_STARTED;
            case "answered", "in-progress" -> ReservationProviderEventType.CALL_CONNECTED;
            case "completed" -> ReservationProviderEventType.CALL_ENDED;
            case "busy" -> ReservationProviderEventType.CALL_BUSY;
            case "no-answer" -> ReservationProviderEventType.CALL_NO_ANSWER;
            case "failed" -> ReservationProviderEventType.CALL_CONNECTION_FAILED;
            default -> throw new IllegalArgumentException("지원하지 않는 ClawOps event/status입니다: " + providerStatus);
        };
    }

    private boolean isRetryable(ReservationProviderEventType eventType) {
        return eventType == ReservationProviderEventType.CALL_BUSY
                || eventType == ReservationProviderEventType.CALL_NO_ANSWER
                || eventType == ReservationProviderEventType.CALL_CONNECTION_FAILED;
    }

    private LocalDateTime parseTimestamp(String value) {
        if (!hasText(value)) {
            throw new IllegalArgumentException("Timestamp는 필수입니다.");
        }
        try {
            return OffsetDateTime.parse(value.trim()).toLocalDateTime();
        } catch (Exception ignored) {
            return LocalDateTime.parse(value.trim());
        }
    }

    private Long parseLong(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("ReservationId 형식이 올바르지 않습니다.");
        }
    }

    private String parameter(Map<String, String> parameters, String name) {
        if (parameters.containsKey(name)) {
            return parameters.get(name);
        }
        return parameters.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
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
