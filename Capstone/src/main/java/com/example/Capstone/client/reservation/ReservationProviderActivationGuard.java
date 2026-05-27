package com.example.Capstone.client.reservation;

import java.util.List;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ReservationProviderActivationGuard {

    private final ReservationProviderProperties providerProperties;
    private final OpenAiRealtimeProperties openAiRealtimeProperties;
    private final TwilioVoiceProperties twilioVoiceProperties;
    private final ClawOpsProperties clawOpsProperties;
    private final Environment environment;

    public ReservationProviderActivationResult verify(
            ReservationProviderMode providerMode,
            ReservationCallStartCommand command
    ) {
        if (providerMode == null || !providerMode.isExternal()) {
            return ReservationProviderActivationResult.success();
        }
        if (!providerProperties.isExternalCallingRequested()) {
            return ReservationProviderActivationResult.rejected(
                    "NOOP_EXTERNAL_CALL_DISABLED",
                    "외부 provider 호출이 비활성화되어 있습니다."
            );
        }
        if (providerProperties.isProdExternalCallBlocked()
                && environment.acceptsProfiles(Profiles.of("prod"))) {
            return ReservationProviderActivationResult.rejected(
                    "NOOP_PROD_PROFILE_BLOCKED",
                    "prod profile에서는 실제 provider PoC 호출을 허용하지 않습니다."
            );
        }
        if (!hasAllowedProfile(providerProperties.getExternalCallAllowedProfiles())) {
            return ReservationProviderActivationResult.rejected(
                    "NOOP_EXTERNAL_CALL_PROFILE_BLOCKED",
                    "실제 provider PoC 호출은 허용된 dev profile에서만 가능합니다."
            );
        }
        if (providerProperties.isRequireAllowlistForExternalCall()
                && (!providerProperties.isCallAllowlistEnabled() || !providerProperties.hasCallAllowlist())) {
            return ReservationProviderActivationResult.rejected(
                    "NOOP_CALL_ALLOWLIST_REQUIRED",
                    "실제 provider PoC 호출에는 allowlist가 필요합니다."
            );
        }
        String providerTargetPhoneNumber = providerProperties.resolveProviderTargetPhoneNumber(
                providerMode,
                command.restaurantPhoneNumber()
        );
        if (!providerProperties.isPhoneNumberAllowlisted(providerTargetPhoneNumber)) {
            return ReservationProviderActivationResult.rejected(
                    "NOOP_CALL_NOT_ALLOWLISTED",
                    "allowlist에 없는 번호로는 실제 provider PoC 호출을 할 수 없습니다."
            );
        }
        if (providerMode == ReservationProviderMode.CLAWOPS
                && (providerProperties.isClawOpsSidecarRuntime()
                || !clawOpsProperties.isLocalHttpContractBaseUrl())
                && !providerProperties.isRealCallEnabled()) {
            return ReservationProviderActivationResult.rejected(
                    "NOOP_CLAWOPS_REAL_CALL_DISABLED",
                    "ClawOps 실제 endpoint 후보 호출은 명시 real-call flag가 필요합니다."
            );
        }
        if (!isProviderConfigurationReady(providerMode)) {
            return ReservationProviderActivationResult.rejected(
                    "NOOP_PROVIDER_CONFIGURATION_MISSING",
                    "실제 provider PoC에 필요한 설정이 부족합니다."
            );
        }

        return ReservationProviderActivationResult.success();
    }

    private boolean hasAllowedProfile(List<String> allowedProfiles) {
        if (allowedProfiles == null || allowedProfiles.isEmpty()) {
            return false;
        }
        return allowedProfiles.stream()
                .filter(profile -> profile != null && !profile.isBlank())
                .anyMatch(profile -> environment.acceptsProfiles(Profiles.of(profile.trim())));
    }

    private boolean isProviderConfigurationReady(ReservationProviderMode providerMode) {
        return switch (providerMode) {
            case TWILIO -> openAiRealtimeProperties.isRealtimeConfigured()
                    && twilioVoiceProperties.isConfigured();
            case OPENAI_SIP -> openAiRealtimeProperties.isRealtimeConfigured()
                    && hasText(openAiRealtimeProperties.getRealtime().getSipEndpoint());
            case CLAWOPS -> providerProperties.isClawOpsSidecarRuntime()
                    ? clawOpsProperties.isSidecarConfigured()
                    : clawOpsProperties.isDirectRestConfigured();
            case GENERIC -> false;
            case MOCK, NOOP -> true;
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
