package com.example.Capstone.client.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

class ReservationProviderPropertiesTest {

    @Test
    @DisplayName("provider 설정 기본값은 mock 모드와 실제 발신 비활성화다")
    void reservationProviderDefaultsAreSafe() {
        ReservationProviderProperties properties = Binder.get(new MockEnvironment())
                .bindOrCreate("reservation.provider", ReservationProviderProperties.class);

        assertThat(properties.effectiveMode()).isEqualTo(ReservationProviderMode.MOCK);
        assertThat(properties.effectiveRuntime()).isEqualTo(ReservationProviderRuntime.DIRECT_REST);
        assertThat(properties.isClawOpsSidecarRuntime()).isFalse();
        assertThat(properties.isCallingEnabled()).isFalse();
        assertThat(properties.isRealCallEnabled()).isFalse();
        assertThat(properties.isExternalCallingRequested()).isFalse();
        assertThat(properties.isCallAllowlistEnabled()).isTrue();
        assertThat(properties.isRequireAllowlistForExternalCall()).isTrue();
        assertThat(properties.isProdExternalCallBlocked()).isTrue();
        assertThat(properties.getExternalCallAllowedProfiles()).containsExactly("dev");
        assertThat(properties.isDevTargetPhoneOverrideEnabled()).isFalse();
        assertThat(properties.hasDevTargetPhoneOverrideNumber()).isFalse();
    }

    @Test
    @DisplayName("provider 설정은 환경변수형 property 이름으로 바인딩할 수 있다")
    void reservationProviderBindsExternalModeAndAllowlist() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("reservation.provider.mode", "clawops")
                .withProperty("reservation.provider.runtime", "clawops-sidecar")
                .withProperty("reservation.provider.calling-enabled", "true")
                .withProperty("reservation.provider.real-call-enabled", "true")
                .withProperty("reservation.provider.call-allowlist-enabled", "true")
                .withProperty("reservation.provider.require-allowlist-for-external-call", "true")
                .withProperty("reservation.provider.prod-external-call-blocked", "true")
                .withProperty("reservation.provider.external-call-allowed-profiles[0]", "dev")
                .withProperty("reservation.provider.call-allowed-numbers[0]", "+15550100001")
                .withProperty("reservation.provider.dev-target-phone-override-enabled", "true")
                .withProperty("reservation.provider.dev-target-phone-override-number", "+15550100001");

        ReservationProviderProperties properties = Binder.get(environment)
                .bindOrCreate("reservation.provider", ReservationProviderProperties.class);

        assertThat(properties.effectiveMode()).isEqualTo(ReservationProviderMode.CLAWOPS);
        assertThat(properties.effectiveRuntime()).isEqualTo(ReservationProviderRuntime.CLAWOPS_SIDECAR);
        assertThat(properties.isClawOpsSidecarRuntime()).isTrue();
        assertThat(properties.isRealCallEnabled()).isTrue();
        assertThat(properties.isExternalCallingRequested()).isTrue();
        assertThat(properties.getExternalCallAllowedProfiles()).containsExactly("dev");
        assertThat(properties.isPhoneNumberAllowed("+15550100001")).isTrue();
        assertThat(properties.isPhoneNumberAllowed("+15550100002")).isFalse();
        assertThat(properties.isDevTargetPhoneOverrideEnabled()).isTrue();
        assertThat(properties.isDevTargetPhoneOverrideAllowlisted()).isTrue();
        assertThat(properties.resolveProviderTargetPhoneNumber(
                ReservationProviderMode.CLAWOPS,
                "+15550100002"
        )).isEqualTo("+15550100001");
    }

    @Test
    @DisplayName("OpenAI Realtime 설정은 secret 없이도 안전하게 비활성 상태로 바인딩된다")
    void openAiPropertiesAreDisabledWithoutSecret() {
        OpenAiRealtimeProperties properties = Binder.get(new MockEnvironment())
                .bindOrCreate("openai", OpenAiRealtimeProperties.class);

        assertThat(properties.isRealtimeConfigured()).isFalse();
        assertThat(properties.getRealtime().getInstructionsVersion()).isEqualTo("reservation-mvp-v1");
        assertThat(properties.getRealtime().getSessionTimeoutSeconds()).isEqualTo(600);
    }

    @Test
    @DisplayName("Twilio 설정은 필수값이 없으면 실제 호출 가능 상태가 아니다")
    void twilioPropertiesAreDisabledWithoutRequiredValues() {
        TwilioVoiceProperties properties = Binder.get(new MockEnvironment())
                .bindOrCreate("twilio", TwilioVoiceProperties.class);

        assertThat(properties.isConfigured()).isFalse();
        assertThat(properties.isWebhookSignatureEnabled()).isTrue();
    }

    @Test
    @DisplayName("ClawOps 설정은 secret 없이도 안전하게 비활성 상태로 바인딩된다")
    void clawOpsPropertiesAreDisabledWithoutRequiredValues() {
        ClawOpsProperties properties = Binder.get(new MockEnvironment())
                .bindOrCreate("clawops", ClawOpsProperties.class);

        assertThat(properties.isConfigured()).isFalse();
        assertThat(properties.getBaseUrl()).isEqualTo("https://api.claw-ops.com");
        assertThat(properties.isLocalHttpContractBaseUrl()).isFalse();
        assertThat(properties.resolvedWebhookSignatureHeader()).isEqualTo("X-Signature");
        assertThat(properties.getCallTimeoutSeconds()).isEqualTo(30);
        assertThat(properties.getAgentRuntimeMode()).isEqualTo("noop");
        assertThat(properties.isSidecarConfigured()).isFalse();
        assertThat(properties.isLocalSidecarBaseUrl()).isFalse();
        assertThat(properties.getSidecarConnectTimeoutSeconds()).isEqualTo(3);
        assertThat(properties.getSidecarReadTimeoutSeconds()).isEqualTo(10);
        assertThat(properties.isSidecarReadinessRequired()).isFalse();
    }

    @Test
    @DisplayName("ClawOps 설정은 환경변수형 property 이름으로 바인딩할 수 있다")
    void clawOpsPropertiesCanBindConfiguration() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("clawops.api-key", "test-api-key")
                .withProperty("clawops.account-id", "test-account")
                .withProperty("clawops.base-url", "http://localhost:9876")
                .withProperty("clawops.from-number", "+15550100003")
                .withProperty("clawops.status-callback-url", "https://example.test/webhooks/clawops")
                .withProperty("clawops.webhook-signing-key", "test-signing-key")
                .withProperty("clawops.webhook-signature-header", "X-ClawOps-Signature")
                .withProperty("clawops.call-timeout-seconds", "45")
                .withProperty("clawops.agent-runtime-mode", "sdk-sidecar")
                .withProperty("clawops.sidecar-base-url", "http://localhost:18080")
                .withProperty("clawops.sidecar-internal-signing-key", "test-sidecar-signing-key")
                .withProperty("clawops.sidecar-connect-timeout-seconds", "2")
                .withProperty("clawops.sidecar-read-timeout-seconds", "7")
                .withProperty("clawops.sidecar-readiness-required", "true");

        ClawOpsProperties properties = Binder.get(environment)
                .bindOrCreate("clawops", ClawOpsProperties.class);

        assertThat(properties.isConfigured()).isTrue();
        assertThat(properties.isLocalHttpContractBaseUrl()).isTrue();
        assertThat(properties.resolvedWebhookSignatureHeader()).isEqualTo("X-ClawOps-Signature");
        assertThat(properties.getCallTimeoutSeconds()).isEqualTo(45);
        assertThat(properties.getAgentRuntimeMode()).isEqualTo("sdk-sidecar");
        assertThat(properties.isSidecarConfigured()).isTrue();
        assertThat(properties.getSidecarBaseUrl()).isEqualTo("http://localhost:18080");
        assertThat(properties.isLocalSidecarBaseUrl()).isTrue();
        assertThat(properties.getSidecarConnectTimeoutSeconds()).isEqualTo(2);
        assertThat(properties.getSidecarReadTimeoutSeconds()).isEqualTo(7);
        assertThat(properties.isSidecarReadinessRequired()).isTrue();
    }
}
