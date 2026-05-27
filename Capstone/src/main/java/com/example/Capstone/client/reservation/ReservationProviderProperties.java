package com.example.Capstone.client.reservation;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "reservation.provider")
public class ReservationProviderProperties {

    private ReservationProviderMode mode = ReservationProviderMode.MOCK;
    private ReservationProviderRuntime runtime = ReservationProviderRuntime.DIRECT_REST;
    private boolean callingEnabled = false;
    private boolean realCallEnabled = false;
    private boolean callAllowlistEnabled = true;
    private boolean requireAllowlistForExternalCall = true;
    private boolean prodExternalCallBlocked = true;
    private List<String> externalCallAllowedProfiles = new ArrayList<>(List.of("dev"));
    private List<String> callAllowedNumbers = new ArrayList<>();
    private String webhookBaseUrl;
    private long webhookMaxClockSkewSeconds = 300;
    private boolean webhookRawPayloadStoreEnabled = false;
    private boolean transcriptStoreEnabled = false;
    private boolean recordingStoreEnabled = false;
    private boolean mockResultApiEnabled = true;
    private boolean devTargetPhoneOverrideEnabled = false;
    private String devTargetPhoneOverrideNumber;

    public ReservationProviderMode effectiveMode() {
        return mode == null ? ReservationProviderMode.MOCK : mode;
    }

    public ReservationProviderRuntime effectiveRuntime() {
        return runtime == null ? ReservationProviderRuntime.DIRECT_REST : runtime;
    }

    public boolean isClawOpsSidecarRuntime() {
        return effectiveMode() == ReservationProviderMode.CLAWOPS
                && effectiveRuntime().isClawOpsSidecar();
    }

    public boolean isExternalCallingRequested() {
        return callingEnabled && effectiveMode().isExternal();
    }

    public boolean isPhoneNumberAllowed(String phoneNumber) {
        if (!callAllowlistEnabled) {
            return true;
        }
        return isPhoneNumberAllowlisted(phoneNumber);
    }

    public boolean hasCallAllowlist() {
        if (callAllowedNumbers == null) {
            return false;
        }
        return callAllowedNumbers.stream()
                .map(this::normalizePhoneNumber)
                .anyMatch(value -> value != null && !value.isBlank());
    }

    public boolean isPhoneNumberAllowlisted(String phoneNumber) {
        String normalizedPhoneNumber = normalizePhoneNumber(phoneNumber);
        if (normalizedPhoneNumber == null) {
            return false;
        }
        if (callAllowedNumbers == null) {
            return false;
        }
        return callAllowedNumbers.stream()
                .map(this::normalizePhoneNumber)
                .anyMatch(normalizedPhoneNumber::equals);
    }

    public boolean hasDevTargetPhoneOverrideNumber() {
        return normalizePhoneNumber(devTargetPhoneOverrideNumber) != null;
    }

    public boolean isDevTargetPhoneOverrideAllowlisted() {
        return isPhoneNumberAllowlisted(devTargetPhoneOverrideNumber);
    }

    public String devTargetPhoneOverrideNumberOrNull() {
        return normalizePhoneNumber(devTargetPhoneOverrideNumber);
    }

    public boolean canUseDevTargetPhoneOverride(ReservationProviderMode providerMode) {
        return providerMode == ReservationProviderMode.CLAWOPS
                && devTargetPhoneOverrideEnabled
                && hasDevTargetPhoneOverrideNumber()
                && isDevTargetPhoneOverrideAllowlisted();
    }

    public String resolveProviderTargetPhoneNumber(
            ReservationProviderMode providerMode,
            String originalPhoneNumber
    ) {
        if (canUseDevTargetPhoneOverride(providerMode)) {
            return devTargetPhoneOverrideNumberOrNull();
        }
        return originalPhoneNumber;
    }

    public String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return null;
        }
        String normalized = phoneNumber.replaceAll("[^0-9+]", "");
        return normalized.isBlank() ? null : normalized;
    }
}
