package com.example.Capstone.dto.response;

import java.util.List;

public record ClawOpsRealCallPreflightResponse(
        boolean realCallCandidate,
        List<String> blockReasons,
        List<String> warnings,
        String targetType,
        String targetPhoneNumberMasked,
        String databasePhoneSnapshotMasked,
        boolean databasePhoneSnapshotNotAllowlisted,
        boolean devTargetPhoneOverrideEnabled,
        boolean devTargetPhoneOverrideNumberPresent,
        String devTargetPhoneOverrideNumberMasked,
        boolean devTargetPhoneOverrideAllowlisted,
        boolean devTargetPhoneOverrideApplied,
        boolean effectiveTargetNumberAllowlisted,
        String providerMode,
        String providerRuntime,
        List<String> activeProfiles,
        boolean devProfileActive,
        boolean prodProfileActive,
        boolean callingEnabled,
        boolean realCallEnabled,
        boolean allowlistEnabled,
        boolean allowlistPresent,
        boolean targetNumberAllowlisted,
        boolean clawOpsConfigured,
        List<String> missingClawOpsSettings,
        boolean schedulerEnabled,
        Long reservationId,
        Long restaurantId,
        String reservationStatus,
        boolean reservationTargetVerified
) {
}
