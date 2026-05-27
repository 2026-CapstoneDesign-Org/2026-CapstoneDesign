package com.example.Capstone.controller;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.Capstone.client.reservation.MockReservationProviderWebhookMapper;
import com.example.Capstone.client.reservation.ReservationProviderEventCommand;
import com.example.Capstone.client.reservation.ReservationProviderWebhookPayload;
import com.example.Capstone.client.reservation.ReservationProviderWebhookSecurityVerifier;
import com.example.Capstone.client.reservation.ReservationProviderWebhookSecurityResult;
import com.example.Capstone.dto.request.MockReservationProviderWebhookRequest;
import com.example.Capstone.dto.response.ReservationProviderWebhookResponse;
import com.example.Capstone.exception.ErrorResponse;
import com.example.Capstone.service.ReservationProviderEventService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/webhooks/reservations/call-providers")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "reservation.webhook.mock.enabled", havingValue = "true")
public class ReservationProviderWebhookController {

    private final ReservationProviderWebhookSecurityVerifier securityVerifier;
    private final MockReservationProviderWebhookMapper mockWebhookMapper;
    private final ReservationProviderEventService providerEventService;
    private final ObjectMapper objectMapper;

    @PostMapping("/{provider}")
    public ResponseEntity<?> receiveMockProviderWebhook(
            @PathVariable String provider,
            @RequestHeader Map<String, String> headers,
            @RequestBody String rawPayload
    ) {
        ReservationProviderWebhookPayload payload = new ReservationProviderWebhookPayload(
                provider,
                headers,
                rawPayload
        );
        ReservationProviderWebhookSecurityResult securityResult = securityVerifier.verify(payload);
        if (!securityResult.verified()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ErrorResponse.of("WEBHOOK_SIGNATURE_INVALID", securityResult.failureReason()));
        }

        MockReservationProviderWebhookRequest request;
        try {
            request = objectMapper.readValue(rawPayload, MockReservationProviderWebhookRequest.class);
        } catch (JsonProcessingException exception) {
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.of("WEBHOOK_PAYLOAD_INVALID", "webhook payload를 해석할 수 없습니다."));
        }

        ReservationProviderEventCommand command;
        try {
            command = mockWebhookMapper.toCommand(provider, request, rawPayload);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.of("WEBHOOK_EVENT_INVALID", exception.getMessage()));
        }

        return ResponseEntity.ok(ReservationProviderWebhookResponse.from(
                providerEventService.processProviderEvent(command)
        ));
    }
}
