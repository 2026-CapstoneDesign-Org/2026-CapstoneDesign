package com.example.Capstone.client.reservation;

import java.util.List;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ClawOpsEndpointAccessPolicy {

    private final ReservationProviderProperties providerProperties;
    private final ClawOpsProperties clawOpsProperties;
    private final Environment environment;

    public ClawOpsEndpointAccessResult verify(
            ReservationProviderMode providerMode,
            String phoneNumber
    ) {
        if (providerProperties.isClawOpsSidecarRuntime()) {
            return ClawOpsEndpointAccessResult.rejected("NOOP_CLAWOPS_DIRECT_REST_RUNTIME_DISABLED");
        }
        if (clawOpsProperties.isLocalHttpContractBaseUrl()) {
            return ClawOpsEndpointAccessResult.localContractAllowed();
        }
        if (providerMode != ReservationProviderMode.CLAWOPS) {
            return ClawOpsEndpointAccessResult.rejected("NOOP_CLAWOPS_PROVIDER_MODE_REQUIRED");
        }
        if (!providerProperties.isExternalCallingRequested()) {
            return ClawOpsEndpointAccessResult.rejected("NOOP_EXTERNAL_CALL_DISABLED");
        }
        if (!providerProperties.isRealCallEnabled()) {
            return ClawOpsEndpointAccessResult.rejected("NOOP_CLAWOPS_REAL_CALL_DISABLED");
        }
        if (providerProperties.isProdExternalCallBlocked()
                && environment.acceptsProfiles(Profiles.of("prod"))) {
            return ClawOpsEndpointAccessResult.rejected("NOOP_PROD_PROFILE_BLOCKED");
        }
        if (!hasAllowedProfile(providerProperties.getExternalCallAllowedProfiles())) {
            return ClawOpsEndpointAccessResult.rejected("NOOP_EXTERNAL_CALL_PROFILE_BLOCKED");
        }
        if (providerProperties.isRequireAllowlistForExternalCall()
                && (!providerProperties.isCallAllowlistEnabled() || !providerProperties.hasCallAllowlist())) {
            return ClawOpsEndpointAccessResult.rejected("NOOP_CALL_ALLOWLIST_REQUIRED");
        }
        if (!providerProperties.isPhoneNumberAllowlisted(phoneNumber)) {
            return ClawOpsEndpointAccessResult.rejected("NOOP_CALL_NOT_ALLOWLISTED");
        }
        if (!clawOpsProperties.isConfigured()) {
            return ClawOpsEndpointAccessResult.rejected("NOOP_PROVIDER_CONFIGURATION_MISSING");
        }

        return ClawOpsEndpointAccessResult.realEndpointAllowed();
    }

    private boolean hasAllowedProfile(List<String> allowedProfiles) {
        if (allowedProfiles == null || allowedProfiles.isEmpty()) {
            return false;
        }
        return allowedProfiles.stream()
                .filter(profile -> profile != null && !profile.isBlank())
                .anyMatch(profile -> environment.acceptsProfiles(Profiles.of(profile.trim())));
    }
}
