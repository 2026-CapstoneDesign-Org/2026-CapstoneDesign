package com.example.Capstone.controller;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.Capstone.client.reservation.ClawOpsProperties;
import com.example.Capstone.client.reservation.ClawOpsSidecarInternalSignature;
import com.example.Capstone.client.reservation.ReservationProviderEventCommand;
import com.example.Capstone.client.reservation.ReservationProviderMode;
import com.example.Capstone.domain.ReservationProviderEventType;
import com.example.Capstone.dto.request.ClawOpsAgentProviderEventRequest;
import com.example.Capstone.dto.response.ReservationProviderWebhookResponse;
import com.example.Capstone.exception.ErrorResponse;
import com.example.Capstone.service.ReservationProviderEventService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/internal/reservations/provider-events")
@RequiredArgsConstructor
public class InternalReservationProviderEventController {

    private static final String DEFAULT_PROVIDER = ReservationProviderMode.CLAWOPS.name() + "_SIDECAR";

    private final ReservationProviderEventService providerEventService;
    private final ClawOpsProperties clawOpsProperties;
    private final com.example.Capstone.client.reservation.ReservationProviderProperties providerProperties;
    private final ObjectMapper objectMapper;

    @PostMapping("/clawops-agent")
    public ResponseEntity<?> receiveClawOpsAgentEvent(
            @RequestHeader HttpHeaders headers,
            @RequestBody String rawPayload
    ) {
        String signatureFailure = verifySignature(headers, rawPayload);
        if (signatureFailure != null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ErrorResponse.of("INTERNAL_SIGNATURE_INVALID", signatureFailure));
        }

        ClawOpsAgentProviderEventRequest request;
        try {
            request = objectMapper.readValue(rawPayload, ClawOpsAgentProviderEventRequest.class);
        } catch (JsonProcessingException exception) {
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.of("INTERNAL_EVENT_PAYLOAD_INVALID", "internal event payload를 해석할 수 없습니다."));
        }

        ReservationProviderEventCommand command = toCommand(request, rawPayload);
        return ResponseEntity.ok(ReservationProviderWebhookResponse.from(
                providerEventService.processProviderEvent(command)
        ));
    }

    private String verifySignature(HttpHeaders headers, String rawPayload) {
        if (!hasText(clawOpsProperties.getSidecarInternalSigningKey())) {
            return "internal signing key가 설정되어 있지 않습니다.";
        }
        String timestamp = headers.getFirst(ClawOpsSidecarInternalSignature.TIMESTAMP_HEADER);
        if (!ClawOpsSidecarInternalSignature.isTimestampAllowed(
                timestamp,
                providerProperties.getWebhookMaxClockSkewSeconds(),
                Clock.systemUTC()
        )) {
            return "timestamp가 허용 범위를 벗어났습니다.";
        }

        String expectedSignature = ClawOpsSidecarInternalSignature.sign(
                clawOpsProperties.getSidecarInternalSigningKey(),
                timestamp,
                rawPayload
        );
        String providedSignature = headers.getFirst(ClawOpsSidecarInternalSignature.SIGNATURE_HEADER);
        if (!ClawOpsSidecarInternalSignature.matches(expectedSignature, providedSignature)) {
            return "signature가 일치하지 않습니다.";
        }
        return null;
    }

    private ReservationProviderEventCommand toCommand(
            ClawOpsAgentProviderEventRequest request,
            String rawPayload
    ) {
        String provider = hasText(request.provider()) ? request.provider().trim().toUpperCase() : DEFAULT_PROVIDER;
        ReservationProviderEventType eventType = request.eventType() == null
                ? ReservationProviderEventType.CALL_QUEUED
                : request.eventType();
        return ReservationProviderEventCommand.builder()
                .provider(provider)
                .providerCallId(firstText(request.providerCallId(), request.sidecarCallId()))
                .providerEventId(request.idempotencyKey())
                .reservationId(request.reservationId())
                .eventType(eventType)
                .providerStatus(request.providerStatus())
                .occurredAt(request.occurredAt() == null ? LocalDateTime.now() : request.occurredAt())
                .retryable(Boolean.TRUE.equals(request.retryable()))
                .failureReason(request.failureReason())
                .aiSummary(request.aiSummary())
                .resultMessage(request.resultMessage())
                .rawPayloadHash(request.rawPayloadHash())
                .rawPayload(rawPayload)
                .signatureVerified(true)
                .build();
    }

    private String firstText(String first, String second) {
        if (hasText(first)) {
            return first;
        }
        return second;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
