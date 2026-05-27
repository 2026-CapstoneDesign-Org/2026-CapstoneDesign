package com.example.Capstone.client.reservation;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import com.example.Capstone.domain.ReservationStatus;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ClawOpsSidecarPhoneProviderClient {

    private final ReservationProviderProperties providerProperties;
    private final ClawOpsProperties clawOpsProperties;
    private final ClawOpsSidecarClient sidecarClient;
    private final Environment environment;

    public PhoneProviderCallStartResult startCall(PhoneProviderCallStartCommand command) {
        Long reservationId = command == null ? null : command.reservationId();
        if (command == null) {
            return disabled(reservationId, "NOOP_CLAWOPS_SIDECAR_COMMAND_MISSING");
        }
        if (command.providerMode() != ReservationProviderMode.CLAWOPS
                || !providerProperties.isClawOpsSidecarRuntime()) {
            return disabled(reservationId, "NOOP_CLAWOPS_SIDECAR_RUNTIME_REQUIRED");
        }
        if (providerProperties.isProdExternalCallBlocked()
                && environment.acceptsProfiles(Profiles.of("prod"))) {
            return disabled(reservationId, "NOOP_PROD_PROFILE_BLOCKED");
        }
        if (providerProperties.isRequireAllowlistForExternalCall()
                && (!providerProperties.isCallAllowlistEnabled() || !providerProperties.hasCallAllowlist())) {
            return disabled(reservationId, "NOOP_CALL_ALLOWLIST_REQUIRED");
        }
        if (!providerProperties.isPhoneNumberAllowlisted(command.toPhoneNumber())) {
            return disabled(reservationId, "NOOP_CALL_NOT_ALLOWLISTED");
        }
        if (!clawOpsProperties.isSidecarConfigured()) {
            return disabled(reservationId, "NOOP_CLAWOPS_SIDECAR_CONFIGURATION_MISSING");
        }
        if (!clawOpsProperties.isLocalSidecarBaseUrl()) {
            return disabled(
                    reservationId,
                    "NOOP_CLAWOPS_SIDECAR_HTTP_CONTRACT_DISABLED",
                    "ClawOps sidecar dry-run HTTP contract는 local endpoint에서만 호출합니다."
            );
        }

        try {
            boolean dryRun = !providerProperties.isRealCallEnabled();
            ClawOpsSidecarCallResponse response = sidecarClient.dispatchDryRun(
                    ClawOpsSidecarCallRequest.from(command, dryRun)
            );
            if (response == null || !response.isAccepted()) {
                return disabled(
                        reservationId,
                        "NOOP_CLAWOPS_SIDECAR_REJECTED",
                        "ClawOps sidecar dry-run 요청이 accepted=true로 응답하지 않았습니다."
                );
            }
            if (!dryRun) {
                return new PhoneProviderCallStartResult(
                        true,
                        ReservationStatus.CALLING,
                        "CLAWOPS_SIDECAR",
                        response.providerCallId(),
                        response.providerStatus(),
                        "ClawOps sidecar real-agent call was accepted."
                );
            }
            return new PhoneProviderCallStartResult(
                    false,
                    ReservationStatus.REQUESTED,
                    "NOOP",
                    "noop-" + reservationId,
                    "NOOP_CLAWOPS_SIDECAR_DRY_RUN_ACCEPTED",
                    "ClawOps sidecar dry-run HTTP contract response was accepted."
            );
        } catch (ClawOpsSidecarClientException exception) {
            return disabled(reservationId, providerStatus(exception), safeFailureMessage(exception));
        } catch (RuntimeException exception) {
            return disabled(reservationId, "NOOP_CLAWOPS_SIDECAR_UNKNOWN_ERROR");
        }
    }

    private PhoneProviderCallStartResult disabled(Long reservationId, String providerStatus) {
        return disabled(
                reservationId,
                providerStatus,
                "ClawOps sidecar client skeleton은 실제 sidecar / ClawOps / OpenAI를 호출하지 않습니다."
        );
    }

    private PhoneProviderCallStartResult disabled(
            Long reservationId,
            String providerStatus,
            String message
    ) {
        return PhoneProviderCallStartResult.disabled(
                reservationId,
                providerStatus,
                message
        );
    }

    private String providerStatus(ClawOpsSidecarClientException exception) {
        return switch (exception.getErrorType()) {
            case HTTP_ERROR -> "NOOP_CLAWOPS_SIDECAR_HTTP_ERROR_" + exception.getHttpStatusCode();
            case TIMEOUT -> "NOOP_CLAWOPS_SIDECAR_TIMEOUT";
            case NETWORK_ERROR -> "NOOP_CLAWOPS_SIDECAR_NETWORK_ERROR";
        };
    }

    private String safeFailureMessage(ClawOpsSidecarClientException exception) {
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
