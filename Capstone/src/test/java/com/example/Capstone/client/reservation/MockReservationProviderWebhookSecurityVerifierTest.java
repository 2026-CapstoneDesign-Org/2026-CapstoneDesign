package com.example.Capstone.client.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MockReservationProviderWebhookSecurityVerifierTest {

    private static final String SECRET = "test-secret";
    private static final Instant NOW = Instant.parse("2026-05-20T09:00:00Z");

    @Test
    @DisplayName("mock webhook signature와 timestamp가 맞으면 보안 검증에 성공한다")
    void verifyAcceptsValidMockSignature() {
        MockReservationProviderWebhookSecurityVerifier verifier = verifier();
        String rawPayload = "{\"event\":\"confirmed\"}";
        String timestamp = NOW.toString();
        String signature = MockReservationProviderWebhookSecurityVerifier.sign(SECRET, timestamp, rawPayload);

        ReservationProviderWebhookSecurityResult result = verifier.verify(new ReservationProviderWebhookPayload(
                "MOCK",
                Map.of(
                        MockReservationProviderWebhookSecurityVerifier.TIMESTAMP_HEADER, timestamp,
                        MockReservationProviderWebhookSecurityVerifier.SIGNATURE_HEADER, signature
                ),
                rawPayload
        ));

        assertThat(result.verified()).isTrue();
        assertThat(result.failureReason()).isNull();
    }

    @Test
    @DisplayName("mock webhook signature가 다르면 보안 검증에 실패한다")
    void verifyRejectsInvalidMockSignature() {
        MockReservationProviderWebhookSecurityVerifier verifier = verifier();

        ReservationProviderWebhookSecurityResult result = verifier.verify(new ReservationProviderWebhookPayload(
                "MOCK",
                Map.of(
                        MockReservationProviderWebhookSecurityVerifier.TIMESTAMP_HEADER, NOW.toString(),
                        MockReservationProviderWebhookSecurityVerifier.SIGNATURE_HEADER, "bad-signature"
                ),
                "{\"event\":\"confirmed\"}"
        ));

        assertThat(result.verified()).isFalse();
        assertThat(result.failureReason()).contains("signature");
    }

    @Test
    @DisplayName("mock webhook timestamp 허용 범위를 벗어나면 보안 검증에 실패한다")
    void verifyRejectsStaleTimestamp() {
        MockReservationProviderWebhookSecurityVerifier verifier = verifier();
        String rawPayload = "{\"event\":\"confirmed\"}";
        String timestamp = NOW.minus(Duration.ofMinutes(10)).toString();
        String signature = MockReservationProviderWebhookSecurityVerifier.sign(SECRET, timestamp, rawPayload);

        ReservationProviderWebhookSecurityResult result = verifier.verify(new ReservationProviderWebhookPayload(
                "MOCK",
                Map.of(
                        MockReservationProviderWebhookSecurityVerifier.TIMESTAMP_HEADER, timestamp,
                        MockReservationProviderWebhookSecurityVerifier.SIGNATURE_HEADER, signature
                ),
                rawPayload
        ));

        assertThat(result.verified()).isFalse();
        assertThat(result.failureReason()).contains("timestamp");
    }

    private MockReservationProviderWebhookSecurityVerifier verifier() {
        return new MockReservationProviderWebhookSecurityVerifier(
                SECRET,
                Duration.ofMinutes(5),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }
}
