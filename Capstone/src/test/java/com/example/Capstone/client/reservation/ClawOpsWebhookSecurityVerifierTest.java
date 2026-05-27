package com.example.Capstone.client.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClawOpsWebhookSecurityVerifierTest {

    private static final Instant NOW = Instant.parse("2026-05-24T10:15:30Z");
    private static final String SIGNING_KEY = "test-clawops-signing-key";
    private static final String CALLBACK_URL = "https://example.test/webhooks/clawops";

    @Test
    @DisplayName("ClawOps webhook signature와 timestamp가 맞으면 보안 검증에 성공한다")
    void verifyAcceptsValidSignature() {
        ClawOpsWebhookSecurityVerifier verifier = verifier();
        String rawPayload = rawPayload(NOW.toString());
        String signature = ClawOpsWebhookSecurityVerifier.sign(
                SIGNING_KEY,
                CALLBACK_URL,
                ClawOpsWebhookFormParser.parse(rawPayload)
        );

        ReservationProviderWebhookSecurityResult result = verifier.verify(new ReservationProviderWebhookPayload(
                "CLAWOPS",
                Map.of("X-Signature", signature),
                rawPayload
        ));

        assertThat(result.verified()).isTrue();
        assertThat(result.failureReason()).isNull();
    }

    @Test
    @DisplayName("ClawOps webhook signature가 다르면 보안 검증에 실패한다")
    void verifyRejectsInvalidSignature() {
        ClawOpsWebhookSecurityVerifier verifier = verifier();

        ReservationProviderWebhookSecurityResult result = verifier.verify(new ReservationProviderWebhookPayload(
                "CLAWOPS",
                Map.of("X-Signature", "bad-signature"),
                rawPayload(NOW.toString())
        ));

        assertThat(result.verified()).isFalse();
        assertThat(result.failureReason()).contains("signature");
    }

    @Test
    @DisplayName("ClawOps webhook timestamp 허용 범위를 벗어나면 보안 검증에 실패한다")
    void verifyRejectsStaleTimestamp() {
        ClawOpsWebhookSecurityVerifier verifier = verifier();
        String staleTimestamp = NOW.minusSeconds(301).toString();
        String rawPayload = rawPayload(staleTimestamp);
        String signature = ClawOpsWebhookSecurityVerifier.sign(
                SIGNING_KEY,
                CALLBACK_URL,
                ClawOpsWebhookFormParser.parse(rawPayload)
        );

        ReservationProviderWebhookSecurityResult result = verifier.verify(new ReservationProviderWebhookPayload(
                "CLAWOPS",
                Map.of("X-Signature", signature),
                rawPayload
        ));

        assertThat(result.verified()).isFalse();
        assertThat(result.failureReason()).contains("timestamp");
    }

    @Test
    @DisplayName("ClawOps webhook 검증 설정이 없으면 안전하게 실패한다")
    void verifyRejectsMissingConfiguration() {
        ClawOpsWebhookSecurityVerifier verifier = new ClawOpsWebhookSecurityVerifier(
                new ClawOpsProperties(),
                providerProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        ReservationProviderWebhookSecurityResult result = verifier.verify(new ReservationProviderWebhookPayload(
                "CLAWOPS",
                Map.of("X-Signature", "signature"),
                rawPayload(NOW.toString())
        ));

        assertThat(result.verified()).isFalse();
        assertThat(result.failureReason()).contains("설정");
    }

    private ClawOpsWebhookSecurityVerifier verifier() {
        return new ClawOpsWebhookSecurityVerifier(
                clawOpsProperties(),
                providerProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private ClawOpsProperties clawOpsProperties() {
        ClawOpsProperties properties = new ClawOpsProperties();
        properties.setApiKey("test-api-key");
        properties.setAccountId("test-account");
        properties.setFromNumber("+15550109999");
        properties.setStatusCallbackUrl(CALLBACK_URL);
        properties.setWebhookSigningKey(SIGNING_KEY);
        return properties;
    }

    private ReservationProviderProperties providerProperties() {
        ReservationProviderProperties properties = new ReservationProviderProperties();
        properties.setWebhookMaxClockSkewSeconds(300);
        return properties;
    }

    private String rawPayload(String timestamp) {
        return "CallId=CA123"
                + "&ReservationId=100"
                + "&CallStatus=answered"
                + "&Timestamp=" + timestamp.replace(":", "%3A");
    }
}
