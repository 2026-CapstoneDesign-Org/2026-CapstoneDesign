package com.example.Capstone.client.reservation;

public record ClawOpsEndpointAccessResult(
        boolean allowed,
        boolean realEndpoint,
        String providerStatus
) {
    public static ClawOpsEndpointAccessResult localContractAllowed() {
        return new ClawOpsEndpointAccessResult(true, false, null);
    }

    public static ClawOpsEndpointAccessResult realEndpointAllowed() {
        return new ClawOpsEndpointAccessResult(true, true, null);
    }

    public static ClawOpsEndpointAccessResult rejected(String providerStatus) {
        return new ClawOpsEndpointAccessResult(false, false, providerStatus);
    }
}
