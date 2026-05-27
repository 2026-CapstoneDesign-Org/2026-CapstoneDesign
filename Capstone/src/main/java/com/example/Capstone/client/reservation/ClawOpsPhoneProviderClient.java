package com.example.Capstone.client.reservation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.Capstone.domain.ReservationStatus;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ClawOpsPhoneProviderClient {

    private static final Logger log = LoggerFactory.getLogger(ClawOpsPhoneProviderClient.class);

    private final ClawOpsProperties clawOpsProperties;
    private final ClawOpsRestClient clawOpsRestClient;
    private final ClawOpsEndpointAccessPolicy endpointAccessPolicy;

    public PhoneProviderCallStartResult startCall(PhoneProviderCallStartCommand command) {
        Long reservationId = command == null ? null : command.reservationId();
        if (command == null) {
            return disabled(reservationId, "NOOP_CLAWOPS_COMMAND_MISSING");
        }
        if (!clawOpsProperties.isConfigured()) {
            return disabled(reservationId, "NOOP_CLAWOPS_CONFIGURATION_MISSING");
        }

        ClawOpsEndpointAccessResult accessResult = endpointAccessPolicy.verify(
                command.providerMode(),
                command.toPhoneNumber()
        );
        if (!accessResult.allowed()) {
            return disabled(reservationId, accessResult.providerStatus());
        }

        try {
            if (accessResult.realEndpoint()) {
                log.warn(
                        "clawops.real-call.candidate reservationId={} restaurantId={} toPhoneMasked={} providerMode={}",
                        command.reservationId(),
                        command.restaurantId(),
                        ClawOpsSafeLogSanitizer.maskPhoneNumber(command.toPhoneNumber()),
                        command.providerMode()
                );
            }
            ClawOpsCreateCallResponse response = clawOpsRestClient.createCall(
                    ClawOpsCreateCallRequest.from(command, clawOpsProperties),
                    accessResult.realEndpoint()
            );
            if (response == null || response.callId() == null || response.callId().isBlank()) {
                return disabled(reservationId, "NOOP_CLAWOPS_RESPONSE_INVALID");
            }
            return new PhoneProviderCallStartResult(
                    true,
                    ReservationStatus.CALLING,
                    "CLAWOPS",
                    response.callId().trim(),
                    normalizedStatus(response.status()),
                    "ClawOps fake server contract response was mapped."
            );
        } catch (ClawOpsRestClientException exception) {
            return handleClawOpsRestFailure(command, exception);
        } catch (RuntimeException exception) {
            log.warn(
                    "clawops.call.failed reservationId={} restaurantId={} providerMode={} errorType={} "
                            + "toPhoneMasked={} fromPhoneMasked={}",
                    command.reservationId(),
                    command.restaurantId(),
                    command.providerMode(),
                    "UNKNOWN",
                    ClawOpsSafeLogSanitizer.maskPhoneNumber(command.toPhoneNumber()),
                    ClawOpsSafeLogSanitizer.maskPhoneNumber(clawOpsProperties.getFromNumber())
            );
            return disabled(reservationId, "NOOP_CLAWOPS_UNKNOWN_ERROR");
        }
    }

    public boolean isConfigured() {
        return clawOpsProperties.isConfigured();
    }

    private PhoneProviderCallStartResult disabled(Long reservationId, String providerStatus) {
        return disabled(
                reservationId,
                providerStatus,
                "ClawOps REST client는 fake server 계약 테스트 외에는 비활성화되어 있습니다."
        );
    }

    private PhoneProviderCallStartResult disabled(Long reservationId, String providerStatus, String message) {
        return PhoneProviderCallStartResult.disabled(
                reservationId,
                providerStatus,
                message
        );
    }

    private String normalizedStatus(String status) {
        if (status == null || status.isBlank()) {
            return "CLAWOPS_CALL_CREATED";
        }
        return status.trim();
    }

    private PhoneProviderCallStartResult handleClawOpsRestFailure(
            PhoneProviderCallStartCommand command,
            ClawOpsRestClientException exception
    ) {
        String providerStatus = providerStatus(exception);
        String safeMessage = safeFailureMessage(exception);
        log.warn(
                "clawops.call.failed reservationId={} restaurantId={} providerMode={} method={} path={} "
                        + "toPhoneMasked={} fromPhoneMasked={} errorType={} httpStatus={} responseBodySummary={}",
                command.reservationId(),
                command.restaurantId(),
                command.providerMode(),
                exception.getMethod(),
                exception.getPath(),
                ClawOpsSafeLogSanitizer.maskPhoneNumber(command.toPhoneNumber()),
                ClawOpsSafeLogSanitizer.maskPhoneNumber(clawOpsProperties.getFromNumber()),
                exception.getErrorType(),
                exception.getHttpStatusCode(),
                exception.getResponseBodySummary()
        );
        return disabled(command.reservationId(), providerStatus, safeMessage);
    }

    private String providerStatus(ClawOpsRestClientException exception) {
        return switch (exception.getErrorType()) {
            case HTTP_ERROR -> "NOOP_CLAWOPS_HTTP_ERROR_" + exception.getHttpStatusCode();
            case TIMEOUT -> "NOOP_CLAWOPS_TIMEOUT";
            case NETWORK_ERROR -> "NOOP_CLAWOPS_NETWORK_ERROR";
        };
    }

    private String safeFailureMessage(ClawOpsRestClientException exception) {
        StringBuilder builder = new StringBuilder();
        builder.append("type=").append(exception.getErrorType());
        if (exception.getHttpStatusCode() != null) {
            builder.append("; httpStatus=").append(exception.getHttpStatusCode());
        }
        builder.append("; method=").append(exception.getMethod());
        builder.append("; path=").append(exception.getPath());
        if (exception.getResponseBodySummary() != null && !exception.getResponseBodySummary().isBlank()) {
            builder.append("; body=").append(exception.getResponseBodySummary());
        }
        return builder.toString();
    }
}
