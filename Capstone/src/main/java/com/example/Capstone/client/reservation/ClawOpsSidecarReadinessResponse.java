package com.example.Capstone.client.reservation;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClawOpsSidecarReadinessResponse(
        Boolean ready,
        String profile,
        Boolean realCallEnabled,
        Integer allowlistCount,
        Boolean clawOpsConfigured,
        Boolean openAiConfigured,
        String agentRuntime,
        List<String> warnings
) {
    public boolean isReady() {
        return Boolean.TRUE.equals(ready);
    }
}
