package com.example.Capstone.client.reservation;

import java.net.URI;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "clawops")
public class ClawOpsProperties {

    private String apiKey;
    private String accountId;
    private String baseUrl = "https://api.claw-ops.com";
    private String fromNumber;
    private String statusCallbackUrl;
    private String webhookSigningKey;
    private String webhookSignatureHeader = "X-Signature";
    private int callTimeoutSeconds = 30;
    private boolean recordingEnabled = false;
    private boolean transcriptEnabled = false;
    private boolean summaryEnabled = false;
    private String agentRuntimeMode = "noop";
    private String sidecarBaseUrl;
    private String sidecarInternalSigningKey;
    private int sidecarConnectTimeoutSeconds = 3;
    private int sidecarReadTimeoutSeconds = 10;
    private boolean sidecarReadinessRequired = false;

    public boolean isConfigured() {
        return isDirectRestConfigured();
    }

    public boolean isDirectRestConfigured() {
        return hasText(apiKey)
                && hasText(accountId)
                && hasText(fromNumber)
                && hasText(statusCallbackUrl)
                && hasText(webhookSigningKey);
    }

    public boolean isSidecarConfigured() {
        return hasText(sidecarBaseUrl)
                && hasText(sidecarInternalSigningKey);
    }

    public String resolvedWebhookSignatureHeader() {
        if (hasText(webhookSignatureHeader)) {
            return webhookSignatureHeader.trim();
        }
        return "X-Signature";
    }

    public boolean isLocalHttpContractBaseUrl() {
        return isLocalHttpUrl(baseUrl);
    }

    public boolean isLocalSidecarBaseUrl() {
        return isLocalHttpUrl(sidecarBaseUrl);
    }

    private boolean isLocalHttpUrl(String value) {
        if (!hasText(value)) {
            return false;
        }
        try {
            URI uri = URI.create(value.trim());
            String host = uri.getHost();
            if (!hasText(host)) {
                return false;
            }
            return Set.of("localhost", "127.0.0.1", "::1").contains(host.toLowerCase());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
