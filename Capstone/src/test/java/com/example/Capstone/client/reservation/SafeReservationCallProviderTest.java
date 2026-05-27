package com.example.Capstone.client.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.LocalDateTime;
import java.util.List;

import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import com.example.Capstone.domain.ReservationStatus;

@ExtendWith(MockitoExtension.class)
class SafeReservationCallProviderTest {

    @Mock
    private OpenAiRealtimeClient openAiRealtimeClient;

    @Mock
    private PhoneProviderClient phoneProviderClient;

    @Mock
    private ClawOpsRestClient clawOpsRestClient;

    @Mock
    private ClawOpsSidecarPhoneProviderClient clawOpsSidecarPhoneProviderClient;

    private final OpenAiRealtimeProperties openAiRealtimeProperties = configuredOpenAiProperties();
    private final TwilioVoiceProperties twilioVoiceProperties = configuredTwilioProperties();
    private final ClawOpsProperties clawOpsProperties = configuredClawOpsProperties();

    @Test
    @DisplayName("기본 mock 모드는 기존 Mock provider 결과를 그대로 반환한다")
    void defaultModeDelegatesToMockProvider() {
        SafeReservationCallProvider provider = provider(defaultProperties());

        ReservationCallStartResult result = provider.startCall(command("+15550100001"));

        assertThat(result.status()).isEqualTo(ReservationStatus.REQUESTED);
        assertThat(result.provider()).isEqualTo("MOCK");
        assertThat(result.providerCallId()).isEqualTo("mock-100");
        assertThat(result.providerStatus()).isEqualTo("MOCK_WAITING_RESULT");
        then(openAiRealtimeClient).shouldHaveNoInteractions();
        then(phoneProviderClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("외부 provider 모드라도 calling-enabled가 false면 실제 client를 호출하지 않는다")
    void externalModeDoesNotCallClientsWhenCallingDisabled() {
        ReservationProviderProperties properties = defaultProperties();
        properties.setMode(ReservationProviderMode.TWILIO);
        properties.setCallingEnabled(false);
        SafeReservationCallProvider provider = provider(properties);

        ReservationCallStartResult result = provider.startCall(command("+15550100001"));

        assertThat(result.provider()).isEqualTo("NOOP");
        assertThat(result.providerCallId()).isEqualTo("noop-100");
        assertThat(result.providerStatus()).isEqualTo("NOOP_EXTERNAL_CALL_DISABLED");
        then(openAiRealtimeClient).shouldHaveNoInteractions();
        then(phoneProviderClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("ClawOps 모드라도 calling-enabled가 false면 adapter까지 진행하지 않는다")
    void clawOpsModeDoesNotCallAdapterWhenCallingDisabled() {
        ReservationProviderProperties properties = defaultProperties();
        properties.setMode(ReservationProviderMode.CLAWOPS);
        properties.setCallingEnabled(false);
        SafeReservationCallProvider provider = provider(properties);

        ReservationCallStartResult result = provider.startCall(command("+15550100001"));

        assertThat(result.provider()).isEqualTo("NOOP");
        assertThat(result.providerStatus()).isEqualTo("NOOP_EXTERNAL_CALL_DISABLED");
        then(openAiRealtimeClient).shouldHaveNoInteractions();
        then(phoneProviderClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("외부 provider 모드에서 allowlist에 없는 번호는 실제 client를 호출하지 않는다")
    void externalModeRejectsNumbersOutsideAllowlist() {
        ReservationProviderProperties properties = defaultProperties();
        properties.setMode(ReservationProviderMode.TWILIO);
        properties.setCallingEnabled(true);
        properties.setCallAllowedNumbers(List.of("+15550100001"));
        SafeReservationCallProvider provider = provider(properties);

        ReservationCallStartResult result = provider.startCall(command("+15550100002"));

        assertThat(result.provider()).isEqualTo("NOOP");
        assertThat(result.providerStatus()).isEqualTo("NOOP_CALL_NOT_ALLOWLISTED");
        then(openAiRealtimeClient).shouldHaveNoInteractions();
        then(phoneProviderClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("prod profile에서는 외부 provider 후보를 선택하지 않는다")
    void externalModeIsBlockedInProdProfile() {
        ReservationProviderProperties properties = externalReadyProperties();
        SafeReservationCallProvider provider = provider(properties, "prod");

        ReservationCallStartResult result = provider.startCall(command("+15550100001"));

        assertThat(result.provider()).isEqualTo("NOOP");
        assertThat(result.providerStatus()).isEqualTo("NOOP_PROD_PROFILE_BLOCKED");
        then(openAiRealtimeClient).shouldHaveNoInteractions();
        then(phoneProviderClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("dev profile이 아니면 외부 provider 후보를 선택하지 않는다")
    void externalModeRequiresDevProfile() {
        ReservationProviderProperties properties = externalReadyProperties();
        SafeReservationCallProvider provider = provider(properties, "local");

        ReservationCallStartResult result = provider.startCall(command("+15550100001"));

        assertThat(result.provider()).isEqualTo("NOOP");
        assertThat(result.providerStatus()).isEqualTo("NOOP_EXTERNAL_CALL_PROFILE_BLOCKED");
        then(openAiRealtimeClient).shouldHaveNoInteractions();
        then(phoneProviderClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("allowlist가 비어 있으면 외부 provider 후보를 선택하지 않는다")
    void externalModeRequiresNonEmptyAllowlist() {
        ReservationProviderProperties properties = externalReadyProperties();
        properties.setCallAllowedNumbers(List.of());
        SafeReservationCallProvider provider = provider(properties);

        ReservationCallStartResult result = provider.startCall(command("+15550100001"));

        assertThat(result.provider()).isEqualTo("NOOP");
        assertThat(result.providerStatus()).isEqualTo("NOOP_CALL_ALLOWLIST_REQUIRED");
        then(openAiRealtimeClient).shouldHaveNoInteractions();
        then(phoneProviderClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("provider 설정이 부족하면 외부 provider 후보를 선택하지 않는다")
    void externalModeRequiresProviderConfiguration() {
        ReservationProviderProperties properties = externalReadyProperties();
        SafeReservationCallProvider provider = provider(
                properties,
                "dev",
                new OpenAiRealtimeProperties(),
                new TwilioVoiceProperties()
        );

        ReservationCallStartResult result = provider.startCall(command("+15550100001"));

        assertThat(result.provider()).isEqualTo("NOOP");
        assertThat(result.providerStatus()).isEqualTo("NOOP_PROVIDER_CONFIGURATION_MISSING");
        then(openAiRealtimeClient).shouldHaveNoInteractions();
        then(phoneProviderClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("ClawOps 설정이 부족하면 외부 provider 후보를 선택하지 않는다")
    void clawOpsModeRequiresProviderConfiguration() {
        ReservationProviderProperties properties = clawOpsReadyProperties();
        properties.setRealCallEnabled(true);
        SafeReservationCallProvider provider = provider(
                properties,
                "dev",
                openAiRealtimeProperties,
                twilioVoiceProperties,
                new ClawOpsProperties()
        );

        ReservationCallStartResult result = provider.startCall(command("+15550100001"));

        assertThat(result.provider()).isEqualTo("NOOP");
        assertThat(result.providerStatus()).isEqualTo("NOOP_PROVIDER_CONFIGURATION_MISSING");
        then(openAiRealtimeClient).shouldHaveNoInteractions();
        then(phoneProviderClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("dev profile, 설정, allowlist가 모두 맞아야 실제 provider 후보 client까지 진행한다")
    void externalModeCanReachProviderCandidateOnlyWhenAllGuardsPass() {
        ReservationProviderProperties properties = externalReadyProperties();
        SafeReservationCallProvider provider = provider(properties);
        given(openAiRealtimeClient.prepareReservationSession(any(OpenAiRealtimeSessionCommand.class)))
                .willReturn(new OpenAiRealtimeSessionResult(
                        true,
                        "openai-session-1",
                        "OPENAI_REALTIME_READY",
                        "ready"
                ));
        given(phoneProviderClient.startCall(any(PhoneProviderCallStartCommand.class)))
                .willReturn(PhoneProviderCallStartResult.disabled(
                        100L,
                        "NOOP_PHONE_PROVIDER_DISABLED",
                        "no real call in test"
                ));

        ReservationCallStartResult result = provider.startCall(command("+15550100001"));

        assertThat(result.provider()).isEqualTo("NOOP");
        assertThat(result.providerStatus()).isEqualTo("NOOP_PHONE_PROVIDER_DISABLED");
        then(openAiRealtimeClient).should().prepareReservationSession(any(OpenAiRealtimeSessionCommand.class));
        then(phoneProviderClient).should().startCall(any(PhoneProviderCallStartCommand.class));
    }

    @Test
    @DisplayName("ClawOps 모드는 real-call-enabled가 false면 phone client까지 진행하지 않는다")
    void clawOpsModeRequiresRealCallFlagBeforePhoneClient() {
        ReservationProviderProperties properties = clawOpsReadyProperties();
        SafeReservationCallProvider provider = provider(properties);

        ReservationCallStartResult result = provider.startCall(command("+15550100001"));

        assertThat(result.provider()).isEqualTo("NOOP");
        assertThat(result.providerStatus()).isEqualTo("NOOP_CLAWOPS_REAL_CALL_DISABLED");
        then(openAiRealtimeClient).shouldHaveNoInteractions();
        then(phoneProviderClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("ClawOps 모드는 dev, real-call, allowlist, 설정이 모두 맞을 때만 phone client 후보까지 진행한다")
    void clawOpsModeReachesPhoneClientOnlyWhenRealCallGatePasses() {
        ReservationProviderProperties properties = clawOpsReadyProperties();
        properties.setRealCallEnabled(true);
        SafeReservationCallProvider provider = provider(properties);
        given(phoneProviderClient.startCall(any(PhoneProviderCallStartCommand.class)))
                .willReturn(PhoneProviderCallStartResult.disabled(
                        100L,
                        "NOOP_CLAWOPS_HTTP_CONTRACT_DISABLED",
                        "no real clawops call in test"
                ));

        ReservationCallStartResult result = provider.startCall(command("+15550100001"));

        assertThat(result.provider()).isEqualTo("NOOP");
        assertThat(result.providerStatus()).isEqualTo("NOOP_CLAWOPS_HTTP_CONTRACT_DISABLED");
        then(openAiRealtimeClient).shouldHaveNoInteractions();
        then(phoneProviderClient).should().startCall(any(PhoneProviderCallStartCommand.class));
    }

    @Test
    @DisplayName("ClawOps 모드도 prod profile에서는 차단된다")
    void clawOpsModeIsBlockedInProdProfile() {
        ReservationProviderProperties properties = clawOpsReadyProperties();
        properties.setRealCallEnabled(true);
        SafeReservationCallProvider provider = provider(properties, "prod");

        ReservationCallStartResult result = provider.startCall(command("+15550100001"));

        assertThat(result.provider()).isEqualTo("NOOP");
        assertThat(result.providerStatus()).isEqualTo("NOOP_PROD_PROFILE_BLOCKED");
        then(openAiRealtimeClient).shouldHaveNoInteractions();
        then(phoneProviderClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("ClawOps 모드도 allowlist에 없는 번호는 차단된다")
    void clawOpsModeRejectsNumbersOutsideAllowlist() {
        ReservationProviderProperties properties = clawOpsReadyProperties();
        properties.setRealCallEnabled(true);
        SafeReservationCallProvider provider = provider(properties);

        ReservationCallStartResult result = provider.startCall(command("+15550100002"));

        assertThat(result.provider()).isEqualTo("NOOP");
        assertThat(result.providerStatus()).isEqualTo("NOOP_CALL_NOT_ALLOWLISTED");
        then(openAiRealtimeClient).shouldHaveNoInteractions();
        then(phoneProviderClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("ClawOps dev override 번호가 allowlist에 있으면 snapshot 번호를 바꾸지 않고 provider 대상만 override한다")
    void clawOpsModeAppliesDevTargetPhoneOverrideAtProviderBoundary() {
        ReservationProviderProperties properties = clawOpsReadyProperties();
        properties.setRealCallEnabled(true);
        properties.setDevTargetPhoneOverrideEnabled(true);
        properties.setDevTargetPhoneOverrideNumber("+15550100001");
        SafeReservationCallProvider provider = provider(properties);
        given(phoneProviderClient.startCall(any(PhoneProviderCallStartCommand.class)))
                .willReturn(PhoneProviderCallStartResult.disabled(
                        100L,
                        "NOOP_CLAWOPS_HTTP_CONTRACT_DISABLED",
                        "no real clawops call in test"
                ));

        ReservationCallStartResult result = provider.startCall(command("+15550100002"));

        ArgumentCaptor<PhoneProviderCallStartCommand> captor =
                ArgumentCaptor.forClass(PhoneProviderCallStartCommand.class);
        assertThat(result.providerStatus()).isEqualTo("NOOP_CLAWOPS_HTTP_CONTRACT_DISABLED");
        then(phoneProviderClient).should().startCall(captor.capture());
        assertThat(captor.getValue().toPhoneNumber()).isEqualTo("+15550100001");
    }

    @Test
    @DisplayName("ClawOps 모드는 allowlist가 비어 있으면 차단된다")
    void clawOpsModeRequiresNonEmptyAllowlist() {
        ReservationProviderProperties properties = clawOpsReadyProperties();
        properties.setRealCallEnabled(true);
        properties.setCallAllowedNumbers(List.of());
        SafeReservationCallProvider provider = provider(properties);

        ReservationCallStartResult result = provider.startCall(command("+15550100001"));

        assertThat(result.provider()).isEqualTo("NOOP");
        assertThat(result.providerStatus()).isEqualTo("NOOP_CALL_ALLOWLIST_REQUIRED");
        then(openAiRealtimeClient).shouldHaveNoInteractions();
        then(phoneProviderClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("ClawOps sidecar runtime은 Spring ClawOps API key 없이 sidecar client 후보까지 진행한다")
    void clawOpsSidecarRuntimeDoesNotRequireSpringClawOpsApiKey() {
        ReservationProviderProperties properties = clawOpsSidecarReadyProperties();
        ClawOpsProperties sidecarProperties = sidecarConfiguredClawOpsProperties();
        SafeReservationCallProvider provider = provider(
                properties,
                "dev",
                openAiRealtimeProperties,
                twilioVoiceProperties,
                sidecarProperties
        );
        given(clawOpsSidecarPhoneProviderClient.startCall(any(PhoneProviderCallStartCommand.class)))
                .willReturn(PhoneProviderCallStartResult.disabled(
                        100L,
                        "NOOP_CLAWOPS_SIDECAR_DRY_RUN_ACCEPTED",
                        "dry-run"
                ));

        ReservationCallStartResult result = provider.startCall(command("+15550100001"));

        assertThat(result.provider()).isEqualTo("NOOP");
        assertThat(result.providerStatus()).isEqualTo("NOOP_CLAWOPS_SIDECAR_DRY_RUN_ACCEPTED");
        then(openAiRealtimeClient).shouldHaveNoInteractions();
        then(phoneProviderClient).shouldHaveNoInteractions();
        then(clawOpsSidecarPhoneProviderClient).should().startCall(any(PhoneProviderCallStartCommand.class));
    }

    @Test
    @DisplayName("ClawOps sidecar runtime은 sidecar 설정이 없으면 client 후보로 진행하지 않는다")
    void clawOpsSidecarRuntimeRequiresSidecarConfiguration() {
        ReservationProviderProperties properties = clawOpsSidecarReadyProperties();
        SafeReservationCallProvider provider = provider(
                properties,
                "dev",
                openAiRealtimeProperties,
                twilioVoiceProperties,
                new ClawOpsProperties()
        );

        ReservationCallStartResult result = provider.startCall(command("+15550100001"));

        assertThat(result.provider()).isEqualTo("NOOP");
        assertThat(result.providerStatus()).isEqualTo("NOOP_PROVIDER_CONFIGURATION_MISSING");
        then(phoneProviderClient).shouldHaveNoInteractions();
        then(clawOpsSidecarPhoneProviderClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("ClawOps sidecar runtime도 prod profile에서는 차단된다")
    void clawOpsSidecarRuntimeIsBlockedInProdProfile() {
        ReservationProviderProperties properties = clawOpsSidecarReadyProperties();
        SafeReservationCallProvider provider = provider(
                properties,
                "prod",
                openAiRealtimeProperties,
                twilioVoiceProperties,
                sidecarConfiguredClawOpsProperties()
        );

        ReservationCallStartResult result = provider.startCall(command("+15550100001"));

        assertThat(result.provider()).isEqualTo("NOOP");
        assertThat(result.providerStatus()).isEqualTo("NOOP_PROD_PROFILE_BLOCKED");
        then(phoneProviderClient).shouldHaveNoInteractions();
        then(clawOpsSidecarPhoneProviderClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("ClawOps sidecar runtime도 allowlist 밖 번호는 차단된다")
    void clawOpsSidecarRuntimeRejectsNumbersOutsideAllowlist() {
        ReservationProviderProperties properties = clawOpsSidecarReadyProperties();
        SafeReservationCallProvider provider = provider(
                properties,
                "dev",
                openAiRealtimeProperties,
                twilioVoiceProperties,
                sidecarConfiguredClawOpsProperties()
        );

        ReservationCallStartResult result = provider.startCall(command("+15550100002"));

        assertThat(result.provider()).isEqualTo("NOOP");
        assertThat(result.providerStatus()).isEqualTo("NOOP_CALL_NOT_ALLOWLISTED");
        then(phoneProviderClient).shouldHaveNoInteractions();
        then(clawOpsSidecarPhoneProviderClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("외부 provider 모드에서 OpenAI no-op 상태면 전화 provider를 호출하지 않는다")
    void externalModeStopsBeforePhoneClientWhenOpenAiNoop() {
        ReservationProviderProperties properties = defaultProperties();
        properties.setMode(ReservationProviderMode.TWILIO);
        properties.setCallingEnabled(true);
        properties.setCallAllowedNumbers(List.of("+15550100001"));
        SafeReservationCallProvider provider = provider(properties);
        given(openAiRealtimeClient.prepareReservationSession(any(OpenAiRealtimeSessionCommand.class)))
                .willReturn(OpenAiRealtimeSessionResult.disabled("test no-op"));

        ReservationCallStartResult result = provider.startCall(command("+15550100001"));

        assertThat(result.provider()).isEqualTo("NOOP");
        assertThat(result.providerStatus()).isEqualTo("NOOP_OPENAI_REALTIME_DISABLED");
        then(phoneProviderClient).should(never()).startCall(any());
    }

    @Test
    @DisplayName("no-op phone provider는 실제 전화를 시작하지 않는 결과를 반환한다")
    void noopPhoneProviderReturnsDisabledResult() {
        NoopPhoneProviderClient provider = new NoopPhoneProviderClient();

        PhoneProviderCallStartResult result = provider.startCall(PhoneProviderCallStartCommand.from(
                ReservationProviderMode.TWILIO,
                command("+15550100001"),
                OpenAiRealtimeSessionResult.disabled("no session")
        ));

        assertThat(result.started()).isFalse();
        assertThat(result.provider()).isEqualTo("NOOP");
        assertThat(result.providerStatus()).isEqualTo("NOOP_PHONE_PROVIDER_DISABLED");
    }

    @Test
    @DisplayName("ClawOps phone provider adapter는 실제 호출 없이 stub 결과를 반환한다")
    void clawOpsPhoneProviderReturnsStubResult() {
        ReservationProviderProperties providerProperties = clawOpsReadyProperties();
        ClawOpsPhoneProviderClient clawOpsClient = new ClawOpsPhoneProviderClient(
                configuredClawOpsProperties(),
                clawOpsRestClient,
                clawOpsEndpointPolicy(providerProperties, configuredClawOpsProperties(), "dev")
        );
        NoopPhoneProviderClient provider = new NoopPhoneProviderClient(clawOpsClient);

        PhoneProviderCallStartResult result = provider.startCall(PhoneProviderCallStartCommand.from(
                ReservationProviderMode.CLAWOPS,
                command("+15550100001"),
                null
        ));

        assertThat(result.started()).isFalse();
        assertThat(result.provider()).isEqualTo("NOOP");
        assertThat(result.providerStatus()).isEqualTo("NOOP_CLAWOPS_REAL_CALL_DISABLED");
        then(clawOpsRestClient).shouldHaveNoInteractions();
    }

    private SafeReservationCallProvider provider(ReservationProviderProperties properties) {
        return provider(properties, "dev");
    }

    private SafeReservationCallProvider provider(ReservationProviderProperties properties, String activeProfile) {
        return provider(properties, activeProfile, openAiRealtimeProperties, twilioVoiceProperties);
    }

    private SafeReservationCallProvider provider(
            ReservationProviderProperties properties,
            String activeProfile,
            OpenAiRealtimeProperties openAiProperties,
            TwilioVoiceProperties twilioProperties
    ) {
        return provider(properties, activeProfile, openAiProperties, twilioProperties, clawOpsProperties);
    }

    private SafeReservationCallProvider provider(
            ReservationProviderProperties properties,
            String activeProfile,
            OpenAiRealtimeProperties openAiProperties,
            TwilioVoiceProperties twilioProperties,
            ClawOpsProperties clawOpsProperties
    ) {
        return new SafeReservationCallProvider(
                properties,
                new MockReservationCallProvider(),
                new ReservationProviderActivationGuard(
                        properties,
                        openAiProperties,
                        twilioProperties,
                        clawOpsProperties,
                        environment(activeProfile)
                ),
                openAiRealtimeClient,
                phoneProviderClient,
                clawOpsSidecarPhoneProviderClient
        );
    }

    private MockEnvironment environment(String activeProfile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfile);
        return environment;
    }

    private ClawOpsEndpointAccessPolicy clawOpsEndpointPolicy(
            ReservationProviderProperties providerProperties,
            ClawOpsProperties clawOpsProperties,
            String activeProfile
    ) {
        return new ClawOpsEndpointAccessPolicy(
                providerProperties,
                clawOpsProperties,
                environment(activeProfile)
        );
    }

    private ReservationProviderProperties defaultProperties() {
        ReservationProviderProperties properties = new ReservationProviderProperties();
        properties.setMode(ReservationProviderMode.MOCK);
        properties.setCallingEnabled(false);
        properties.setCallAllowlistEnabled(true);
        return properties;
    }

    private ReservationProviderProperties externalReadyProperties() {
        ReservationProviderProperties properties = defaultProperties();
        properties.setMode(ReservationProviderMode.TWILIO);
        properties.setCallingEnabled(true);
        properties.setCallAllowlistEnabled(true);
        properties.setRequireAllowlistForExternalCall(true);
        properties.setProdExternalCallBlocked(true);
        properties.setExternalCallAllowedProfiles(List.of("dev"));
        properties.setCallAllowedNumbers(List.of("+15550100001"));
        return properties;
    }

    private ReservationProviderProperties clawOpsReadyProperties() {
        ReservationProviderProperties properties = externalReadyProperties();
        properties.setMode(ReservationProviderMode.CLAWOPS);
        return properties;
    }

    private ReservationProviderProperties clawOpsSidecarReadyProperties() {
        ReservationProviderProperties properties = clawOpsReadyProperties();
        properties.setRuntime(ReservationProviderRuntime.CLAWOPS_SIDECAR);
        properties.setRealCallEnabled(true);
        return properties;
    }

    private OpenAiRealtimeProperties configuredOpenAiProperties() {
        OpenAiRealtimeProperties properties = new OpenAiRealtimeProperties();
        properties.setApiKey("test-openai-api-key");
        properties.getRealtime().setModel("test-realtime-model");
        return properties;
    }

    private TwilioVoiceProperties configuredTwilioProperties() {
        TwilioVoiceProperties properties = new TwilioVoiceProperties();
        properties.setAccountSid("test-account-sid");
        properties.setAuthToken("test-auth-token");
        properties.setFromNumber("+821012345678");
        properties.setStatusCallbackUrl("https://example.test/webhooks/twilio");
        return properties;
    }

    private ClawOpsProperties configuredClawOpsProperties() {
        ClawOpsProperties properties = new ClawOpsProperties();
        properties.setApiKey("test-clawops-api-key");
        properties.setAccountId("test-clawops-account-id");
        properties.setFromNumber("+15550100003");
        properties.setStatusCallbackUrl("https://example.test/webhooks/clawops");
        properties.setWebhookSigningKey("test-clawops-signing-key");
        return properties;
    }

    private ClawOpsProperties sidecarConfiguredClawOpsProperties() {
        ClawOpsProperties properties = new ClawOpsProperties();
        properties.setSidecarBaseUrl("http://localhost:18080");
        properties.setSidecarInternalSigningKey("test-sidecar-signing-key");
        return properties;
    }

    private ReservationCallStartCommand command(String phoneNumber) {
        return new ReservationCallStartCommand(
                100L,
                10L,
                "예약식당",
                phoneNumber,
                LocalDateTime.of(2026, 6, 1, 19, 0),
                4,
                "창가 자리"
        );
    }
}
