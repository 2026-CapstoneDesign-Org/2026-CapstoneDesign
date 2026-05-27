package com.example.Capstone.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Capstone.client.reservation.ClawOpsProperties;
import com.example.Capstone.client.reservation.ClawOpsSidecarClient;
import com.example.Capstone.client.reservation.ClawOpsSidecarReadinessCheckResult;
import com.example.Capstone.client.reservation.ReservationProviderMode;
import com.example.Capstone.client.reservation.ReservationProviderProperties;
import com.example.Capstone.client.reservation.ReservationProviderRuntime;
import com.example.Capstone.domain.ReservationStatus;
import com.example.Capstone.domain.RestaurantReservation;
import com.example.Capstone.dto.request.ClawOpsRealCallPreflightRequest;
import com.example.Capstone.dto.response.ClawOpsRealCallPreflightResponse;
import com.example.Capstone.repository.RestaurantReservationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClawOpsRealCallPreflightService {

    private static final Logger log = LoggerFactory.getLogger(ClawOpsRealCallPreflightService.class);

    private final RestaurantReservationRepository reservationRepository;
    private final ReservationProviderProperties providerProperties;
    private final ClawOpsProperties clawOpsProperties;
    private final ClawOpsSidecarClient sidecarClient;
    private final Environment environment;

    public ClawOpsRealCallPreflightResponse preflight(ClawOpsRealCallPreflightRequest request) {
        List<String> blockReasons = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Long reservationId = request == null ? null : request.reservationId();
        String requestedPhoneNumber = request == null ? null : request.targetPhoneNumber();

        ReservationSnapshot reservationSnapshot = resolveReservationSnapshot(
                reservationId,
                requestedPhoneNumber,
                blockReasons,
                warnings
        );
        String targetPhoneNumber = reservationSnapshot.targetPhoneNumber();
        boolean devProfileActive = environment.acceptsProfiles(Profiles.of("dev"));
        boolean prodProfileActive = environment.acceptsProfiles(Profiles.of("prod"));
        boolean schedulerEnabled = environment.getProperty("reservation.scheduler.enabled", Boolean.class, false);
        ReservationProviderMode providerMode = providerProperties.effectiveMode();
        ReservationProviderRuntime providerRuntime = providerProperties.effectiveRuntime();
        boolean allowlistPresent = providerProperties.hasCallAllowlist();
        boolean targetNumberAllowlisted = providerProperties.isPhoneNumberAllowlisted(targetPhoneNumber);
        boolean devTargetPhoneOverrideEnabled = providerProperties.isDevTargetPhoneOverrideEnabled();
        boolean devTargetPhoneOverrideNumberPresent = providerProperties.hasDevTargetPhoneOverrideNumber();
        String overrideTargetPhoneNumber = providerProperties.devTargetPhoneOverrideNumberOrNull();
        boolean devTargetPhoneOverrideAllowlisted = providerProperties.isDevTargetPhoneOverrideAllowlisted();
        List<String> missingClawOpsSettings = missingClawOpsSettings(providerRuntime);
        boolean clawOpsConfigured = missingClawOpsSettings.isEmpty();
        boolean baseDevOverrideUsable = devProfileActive
                && !prodProfileActive
                && providerMode == ReservationProviderMode.CLAWOPS
                && providerProperties.isCallingEnabled()
                && providerProperties.isRealCallEnabled()
                && providerProperties.isCallAllowlistEnabled()
                && allowlistPresent
                && devTargetPhoneOverrideEnabled
                && devTargetPhoneOverrideNumberPresent
                && devTargetPhoneOverrideAllowlisted
                && clawOpsConfigured;
        boolean devTargetPhoneOverrideAvailable = hasText(targetPhoneNumber)
                && !targetNumberAllowlisted
                && baseDevOverrideUsable;
        String effectiveTargetPhoneNumber = devTargetPhoneOverrideAvailable
                ? overrideTargetPhoneNumber
                : targetPhoneNumber;
        boolean effectiveTargetAllowlisted = providerProperties.isPhoneNumberAllowlisted(effectiveTargetPhoneNumber);

        if (!devProfileActive) {
            blockReasons.add("DEV_PROFILE_REQUIRED");
        }
        if (prodProfileActive) {
            blockReasons.add("PROD_PROFILE_BLOCKED");
        }
        if (providerMode != ReservationProviderMode.CLAWOPS) {
            blockReasons.add("CLAWOPS_MODE_REQUIRED");
        }
        if (!providerProperties.isCallingEnabled()) {
            blockReasons.add("CALLING_DISABLED");
        }
        if (!providerProperties.isRealCallEnabled()) {
            blockReasons.add("REAL_CALL_DISABLED");
        }
        if (!providerProperties.isCallAllowlistEnabled()) {
            blockReasons.add("ALLOWLIST_DISABLED");
        }
        if (!allowlistPresent) {
            blockReasons.add("ALLOWLIST_EMPTY");
        }
        if (!hasText(targetPhoneNumber)) {
            blockReasons.add("TARGET_PHONE_REQUIRED");
        } else if (!targetNumberAllowlisted) {
            if (devTargetPhoneOverrideAvailable) {
                warnings.add("DB_PHONE_SNAPSHOT_NOT_ALLOWLISTED_DEV_OVERRIDE_APPLIED");
            } else {
                blockReasons.add("TARGET_NOT_ALLOWLISTED");
            }
        }
        addDevOverrideBlockReasons(
                targetPhoneNumber,
                targetNumberAllowlisted,
                devTargetPhoneOverrideEnabled,
                devTargetPhoneOverrideNumberPresent,
                devTargetPhoneOverrideAllowlisted,
                blockReasons
        );
        if (!clawOpsConfigured) {
            blockReasons.add("CLAWOPS_REQUIRED_SETTINGS_MISSING");
        }
        if (!providerRuntime.isClawOpsSidecar()
                && clawOpsProperties.isLocalHttpContractBaseUrl()) {
            blockReasons.add("CLAWOPS_REAL_ENDPOINT_REQUIRED");
        }
        if (providerRuntime.isClawOpsSidecar()
                && clawOpsConfigured
                && !clawOpsProperties.isLocalSidecarBaseUrl()) {
            blockReasons.add("CLAWOPS_SIDECAR_HTTP_CONTRACT_DISABLED");
        }
        if (providerRuntime.isClawOpsSidecar()
                && clawOpsConfigured
                && clawOpsProperties.isLocalSidecarBaseUrl()
                && clawOpsProperties.isSidecarReadinessRequired()) {
            ClawOpsSidecarReadinessCheckResult readinessResult = sidecarClient.checkReadiness();
            if (!readinessResult.ready()) {
                blockReasons.add(readinessResult.blockReason());
            }
        }
        if (schedulerEnabled) {
            blockReasons.add("SCHEDULER_ENABLED");
        }

        boolean realCallCandidate = blockReasons.isEmpty();
        log.info(
                "clawops.preflight realCallCandidate={} providerMode={} providerRuntime={} "
                        + "targetPhoneMasked={} overrideApplied={} reasons={} warnings={}",
                realCallCandidate,
                providerMode,
                providerRuntime,
                maskPhoneNumber(effectiveTargetPhoneNumber),
                devTargetPhoneOverrideAvailable,
                blockReasons,
                warnings
        );

        return new ClawOpsRealCallPreflightResponse(
                realCallCandidate,
                List.copyOf(blockReasons),
                List.copyOf(warnings),
                reservationSnapshot.targetType(),
                maskPhoneNumber(effectiveTargetPhoneNumber),
                maskPhoneNumber(targetPhoneNumber),
                !targetNumberAllowlisted && allowlistPresent,
                devTargetPhoneOverrideEnabled,
                devTargetPhoneOverrideNumberPresent,
                maskPhoneNumber(overrideTargetPhoneNumber),
                devTargetPhoneOverrideAllowlisted,
                devTargetPhoneOverrideAvailable,
                effectiveTargetAllowlisted,
                providerMode.name(),
                providerRuntime.name(),
                activeProfiles(),
                devProfileActive,
                prodProfileActive,
                providerProperties.isCallingEnabled(),
                providerProperties.isRealCallEnabled(),
                providerProperties.isCallAllowlistEnabled(),
                allowlistPresent,
                targetNumberAllowlisted,
                clawOpsConfigured,
                List.copyOf(missingClawOpsSettings),
                schedulerEnabled,
                reservationSnapshot.reservationId(),
                reservationSnapshot.restaurantId(),
                reservationSnapshot.reservationStatus(),
                reservationSnapshot.reservationTargetVerified()
        );
    }

    private ReservationSnapshot resolveReservationSnapshot(
            Long reservationId,
            String requestedPhoneNumber,
            List<String> blockReasons,
            List<String> warnings
    ) {
        if (reservationId == null) {
            warnings.add("RESERVATION_NOT_SELECTED");
            return new ReservationSnapshot(
                    "PHONE_NUMBER",
                    null,
                    null,
                    null,
                    requestedPhoneNumber,
                    false
            );
        }

        return reservationRepository.findById(reservationId)
                .map(reservation -> fromReservation(reservation, requestedPhoneNumber, blockReasons))
                .orElseGet(() -> {
                    blockReasons.add("RESERVATION_NOT_FOUND");
                    return new ReservationSnapshot(
                            "RESERVATION",
                            reservationId,
                            null,
                            null,
                            requestedPhoneNumber,
                            false
                    );
                });
    }

    private ReservationSnapshot fromReservation(
            RestaurantReservation reservation,
            String requestedPhoneNumber,
            List<String> blockReasons
    ) {
        String snapshotPhoneNumber = reservation.getRestaurantPhoneNumberSnapshot();
        String normalizedRequestedPhoneNumber = normalizePhoneNumber(requestedPhoneNumber);
        String normalizedSnapshotPhoneNumber = normalizePhoneNumber(snapshotPhoneNumber);
        if (normalizedRequestedPhoneNumber != null
                && !normalizedRequestedPhoneNumber.equals(normalizedSnapshotPhoneNumber)) {
            blockReasons.add("TARGET_PHONE_MISMATCHES_RESERVATION_SNAPSHOT");
        }
        if (reservation.getStatus() != ReservationStatus.REQUESTED) {
            blockReasons.add("RESERVATION_NOT_REQUESTED");
        }

        return new ReservationSnapshot(
                "RESERVATION",
                reservation.getId(),
                reservation.getRestaurant() == null ? null : reservation.getRestaurant().getId(),
                reservation.getStatus() == null ? null : reservation.getStatus().name(),
                snapshotPhoneNumber,
                normalizedSnapshotPhoneNumber != null
                        && providerProperties.isPhoneNumberAllowlisted(snapshotPhoneNumber)
                        && reservation.getStatus() == ReservationStatus.REQUESTED
        );
    }

    private List<String> missingClawOpsSettings(ReservationProviderRuntime providerRuntime) {
        List<String> missingSettings = new ArrayList<>();
        if (providerRuntime.isClawOpsSidecar()) {
            addMissingIfBlank(missingSettings, "CLAWOPS_SIDECAR_BASE_URL", clawOpsProperties.getSidecarBaseUrl());
            addMissingIfBlank(
                    missingSettings,
                    "CLAWOPS_SIDECAR_INTERNAL_SIGNING_KEY",
                    clawOpsProperties.getSidecarInternalSigningKey()
            );
            return missingSettings;
        }
        addMissingIfBlank(missingSettings, "CLAWOPS_API_KEY", clawOpsProperties.getApiKey());
        addMissingIfBlank(missingSettings, "CLAWOPS_ACCOUNT_ID", clawOpsProperties.getAccountId());
        addMissingIfBlank(missingSettings, "CLAWOPS_BASE_URL", clawOpsProperties.getBaseUrl());
        addMissingIfBlank(missingSettings, "CLAWOPS_FROM_NUMBER", clawOpsProperties.getFromNumber());
        addMissingIfBlank(missingSettings, "CLAWOPS_STATUS_CALLBACK_URL", clawOpsProperties.getStatusCallbackUrl());
        addMissingIfBlank(missingSettings, "CLAWOPS_WEBHOOK_SIGNING_KEY", clawOpsProperties.getWebhookSigningKey());
        return missingSettings;
    }

    private void addMissingIfBlank(List<String> missingSettings, String settingName, String value) {
        if (!hasText(value)) {
            missingSettings.add(settingName);
        }
    }

    private void addDevOverrideBlockReasons(
            String targetPhoneNumber,
            boolean targetNumberAllowlisted,
            boolean devTargetPhoneOverrideEnabled,
            boolean devTargetPhoneOverrideNumberPresent,
            boolean devTargetPhoneOverrideAllowlisted,
            List<String> blockReasons
    ) {
        if (!hasText(targetPhoneNumber) || targetNumberAllowlisted) {
            return;
        }
        if (!devTargetPhoneOverrideEnabled) {
            blockReasons.add("DEV_TARGET_PHONE_OVERRIDE_DISABLED");
            return;
        }
        if (!devTargetPhoneOverrideNumberPresent) {
            blockReasons.add("DEV_TARGET_PHONE_OVERRIDE_NUMBER_MISSING");
            return;
        }
        if (!devTargetPhoneOverrideAllowlisted) {
            blockReasons.add("DEV_TARGET_PHONE_OVERRIDE_NOT_ALLOWLISTED");
        }
    }

    private List<String> activeProfiles() {
        return Arrays.stream(environment.getActiveProfiles())
                .filter(this::hasText)
                .toList();
    }

    private String maskPhoneNumber(String phoneNumber) {
        String normalized = normalizePhoneNumber(phoneNumber);
        if (!hasText(normalized)) {
            return null;
        }
        if (normalized.length() <= 4) {
            return "****";
        }
        return "****" + normalized.substring(normalized.length() - 4);
    }

    private String normalizePhoneNumber(String phoneNumber) {
        if (!hasText(phoneNumber)) {
            return null;
        }
        String normalized = phoneNumber.replaceAll("[^0-9+]", "");
        return normalized.isBlank() ? null : normalized;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record ReservationSnapshot(
            String targetType,
            Long reservationId,
            Long restaurantId,
            String reservationStatus,
            String targetPhoneNumber,
            boolean reservationTargetVerified
    ) {
    }
}
