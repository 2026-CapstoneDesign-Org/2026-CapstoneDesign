package com.example.Capstone.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import com.example.Capstone.client.reservation.ClawOpsSidecarClient;
import com.example.Capstone.client.reservation.ClawOpsSidecarInternalSignature;
import com.example.Capstone.client.reservation.ClawOpsProperties;
import com.example.Capstone.client.reservation.ReservationProviderMode;
import com.example.Capstone.client.reservation.ReservationProviderProperties;
import com.example.Capstone.client.reservation.ReservationProviderRuntime;
import com.example.Capstone.domain.ReservationStatus;
import com.example.Capstone.domain.Restaurant;
import com.example.Capstone.domain.RestaurantReservation;
import com.example.Capstone.domain.User;
import com.example.Capstone.dto.request.ClawOpsRealCallPreflightRequest;
import com.example.Capstone.dto.response.ClawOpsRealCallPreflightResponse;
import com.example.Capstone.repository.RestaurantReservationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

@ExtendWith(MockitoExtension.class)
class ClawOpsRealCallPreflightServiceTest {

    private static final String TEST_PHONE_NUMBER = "01012345678";
    private static final String TEST_SIDECAR_SIGNING_KEY = "test-sidecar-signing-key";

    @Mock
    private RestaurantReservationRepository reservationRepository;

    @Test
    @DisplayName("기본 설정에서는 ClawOps 실제 발신 후보가 차단된다")
    void defaultSettingsReturnBlockedPreflight() {
        ClawOpsRealCallPreflightService service = service(
                new ReservationProviderProperties(),
                new ClawOpsProperties(),
                environment("db")
        );

        ClawOpsRealCallPreflightResponse response = service.preflight(phoneRequest(TEST_PHONE_NUMBER));

        assertThat(response.realCallCandidate()).isFalse();
        assertThat(response.blockReasons()).contains(
                "DEV_PROFILE_REQUIRED",
                "CLAWOPS_MODE_REQUIRED",
                "CALLING_DISABLED",
                "REAL_CALL_DISABLED",
                "ALLOWLIST_EMPTY",
                "TARGET_NOT_ALLOWLISTED",
                "CLAWOPS_REQUIRED_SETTINGS_MISSING"
        );
    }

    @Test
    @DisplayName("real-call-enabled가 false면 ClawOps 실제 발신 후보가 차단된다")
    void realCallDisabledBlocksPreflight() {
        ReservationProviderProperties providerProperties = readyProviderProperties();
        providerProperties.setRealCallEnabled(false);

        ClawOpsRealCallPreflightResponse response = service(
                providerProperties,
                configuredClawOpsProperties(),
                environment("dev")
        ).preflight(phoneRequest(TEST_PHONE_NUMBER));

        assertThat(response.realCallCandidate()).isFalse();
        assertThat(response.blockReasons()).contains("REAL_CALL_DISABLED");
    }

    @Test
    @DisplayName("prod profile에서는 모든 설정이 맞아도 ClawOps 실제 발신 후보가 차단된다")
    void prodProfileBlocksPreflight() {
        ClawOpsRealCallPreflightResponse response = service(
                readyProviderProperties(),
                configuredClawOpsProperties(),
                environment("dev", "prod")
        ).preflight(phoneRequest(TEST_PHONE_NUMBER));

        assertThat(response.realCallCandidate()).isFalse();
        assertThat(response.prodProfileActive()).isTrue();
        assertThat(response.blockReasons()).contains("PROD_PROFILE_BLOCKED");
    }

    @Test
    @DisplayName("allowlist가 비어 있으면 ClawOps 실제 발신 후보가 차단된다")
    void emptyAllowlistBlocksPreflight() {
        ReservationProviderProperties providerProperties = readyProviderProperties();
        providerProperties.setCallAllowedNumbers(List.of());

        ClawOpsRealCallPreflightResponse response = service(
                providerProperties,
                configuredClawOpsProperties(),
                environment("dev")
        ).preflight(phoneRequest(TEST_PHONE_NUMBER));

        assertThat(response.realCallCandidate()).isFalse();
        assertThat(response.allowlistPresent()).isFalse();
        assertThat(response.blockReasons()).contains("ALLOWLIST_EMPTY");
    }

    @Test
    @DisplayName("allowlist에 없는 번호는 ClawOps 실제 발신 후보가 차단된다")
    void nonAllowlistedNumberBlocksPreflight() {
        ClawOpsRealCallPreflightResponse response = service(
                readyProviderProperties(),
                configuredClawOpsProperties(),
                environment("dev")
        ).preflight(phoneRequest("01000000000"));

        assertThat(response.realCallCandidate()).isFalse();
        assertThat(response.targetNumberAllowlisted()).isFalse();
        assertThat(response.blockReasons()).contains("TARGET_NOT_ALLOWLISTED");
    }

    @Test
    @DisplayName("override 기본값은 false라서 DB snapshot이 allowlist 밖이면 차단된다")
    void devTargetPhoneOverrideDefaultsToDisabled() {
        RestaurantReservation reservation = reservation(101L, "01000000000");
        given(reservationRepository.findById(101L)).willReturn(Optional.of(reservation));

        ClawOpsRealCallPreflightResponse response = service(
                readyProviderProperties(),
                configuredClawOpsProperties(),
                environment("dev")
        ).preflight(new ClawOpsRealCallPreflightRequest(101L, null));

        assertThat(response.realCallCandidate()).isFalse();
        assertThat(response.devTargetPhoneOverrideEnabled()).isFalse();
        assertThat(response.blockReasons()).contains(
                "TARGET_NOT_ALLOWLISTED",
                "DEV_TARGET_PHONE_OVERRIDE_DISABLED"
        );
    }

    @Test
    @DisplayName("prod profile에서는 dev override 조건이 맞아도 preflight가 차단된다")
    void prodProfileBlocksDevTargetPhoneOverride() {
        ReservationProviderProperties providerProperties = readyProviderPropertiesWithOverride(TEST_PHONE_NUMBER);
        RestaurantReservation reservation = reservation(102L, "01000000000");
        given(reservationRepository.findById(102L)).willReturn(Optional.of(reservation));

        ClawOpsRealCallPreflightResponse response = service(
                providerProperties,
                configuredClawOpsProperties(),
                environment("dev", "prod")
        ).preflight(new ClawOpsRealCallPreflightRequest(102L, null));

        assertThat(response.realCallCandidate()).isFalse();
        assertThat(response.prodProfileActive()).isTrue();
        assertThat(response.devTargetPhoneOverrideApplied()).isFalse();
        assertThat(response.blockReasons()).contains("PROD_PROFILE_BLOCKED", "TARGET_NOT_ALLOWLISTED");
    }

    @Test
    @DisplayName("override enabled지만 override 번호가 없으면 preflight가 차단된다")
    void missingDevTargetPhoneOverrideNumberBlocksPreflight() {
        ReservationProviderProperties providerProperties = readyProviderProperties();
        providerProperties.setDevTargetPhoneOverrideEnabled(true);
        RestaurantReservation reservation = reservation(103L, "01000000000");
        given(reservationRepository.findById(103L)).willReturn(Optional.of(reservation));

        ClawOpsRealCallPreflightResponse response = service(
                providerProperties,
                configuredClawOpsProperties(),
                environment("dev")
        ).preflight(new ClawOpsRealCallPreflightRequest(103L, null));

        assertThat(response.realCallCandidate()).isFalse();
        assertThat(response.devTargetPhoneOverrideEnabled()).isTrue();
        assertThat(response.devTargetPhoneOverrideNumberPresent()).isFalse();
        assertThat(response.blockReasons()).contains(
                "TARGET_NOT_ALLOWLISTED",
                "DEV_TARGET_PHONE_OVERRIDE_NUMBER_MISSING"
        );
    }

    @Test
    @DisplayName("override 번호가 allowlist 밖이면 preflight가 차단된다")
    void nonAllowlistedDevTargetPhoneOverrideBlocksPreflight() {
        ReservationProviderProperties providerProperties = readyProviderPropertiesWithOverride("01011112222");
        RestaurantReservation reservation = reservation(104L, "01000000000");
        given(reservationRepository.findById(104L)).willReturn(Optional.of(reservation));

        ClawOpsRealCallPreflightResponse response = service(
                providerProperties,
                configuredClawOpsProperties(),
                environment("dev")
        ).preflight(new ClawOpsRealCallPreflightRequest(104L, null));

        assertThat(response.realCallCandidate()).isFalse();
        assertThat(response.devTargetPhoneOverrideAllowlisted()).isFalse();
        assertThat(response.blockReasons()).contains(
                "TARGET_NOT_ALLOWLISTED",
                "DEV_TARGET_PHONE_OVERRIDE_NOT_ALLOWLISTED"
        );
    }

    @Test
    @DisplayName("DB snapshot과 override target이 달라도 override target이 allowlist면 dev PoC 후보로 인정한다")
    void allowlistedDevTargetPhoneOverrideCanPassReservationPreflight() {
        ReservationProviderProperties providerProperties = readyProviderPropertiesWithOverride(TEST_PHONE_NUMBER);
        RestaurantReservation reservation = reservation(105L, "01000000000");
        given(reservationRepository.findById(105L)).willReturn(Optional.of(reservation));

        ClawOpsRealCallPreflightResponse response = service(
                providerProperties,
                configuredClawOpsProperties(),
                environment("dev")
        ).preflight(new ClawOpsRealCallPreflightRequest(105L, null));

        assertThat(response.realCallCandidate()).isTrue();
        assertThat(response.blockReasons()).isEmpty();
        assertThat(response.targetNumberAllowlisted()).isFalse();
        assertThat(response.effectiveTargetNumberAllowlisted()).isTrue();
        assertThat(response.databasePhoneSnapshotNotAllowlisted()).isTrue();
        assertThat(response.devTargetPhoneOverrideApplied()).isTrue();
        assertThat(response.targetPhoneNumberMasked()).isEqualTo("****5678");
        assertThat(response.databasePhoneSnapshotMasked()).isEqualTo("****0000");
        assertThat(response.devTargetPhoneOverrideNumberMasked()).isEqualTo("****5678");
        assertThat(response.warnings()).contains("DB_PHONE_SNAPSHOT_NOT_ALLOWLISTED_DEV_OVERRIDE_APPLIED");
        assertThat(response.reservationTargetVerified()).isFalse();
    }

    @Test
    @DisplayName("ClawOps 필수 설정이 누락되면 ClawOps 실제 발신 후보가 차단된다")
    void missingClawOpsSettingsBlockPreflight() {
        ClawOpsProperties clawOpsProperties = configuredClawOpsProperties();
        clawOpsProperties.setApiKey(null);

        ClawOpsRealCallPreflightResponse response = service(
                readyProviderProperties(),
                clawOpsProperties,
                environment("dev")
        ).preflight(phoneRequest(TEST_PHONE_NUMBER));

        assertThat(response.realCallCandidate()).isFalse();
        assertThat(response.clawOpsConfigured()).isFalse();
        assertThat(response.blockReasons()).contains("CLAWOPS_REQUIRED_SETTINGS_MISSING");
        assertThat(response.missingClawOpsSettings()).containsExactly("CLAWOPS_API_KEY");
    }

    @Test
    @DisplayName("scheduler가 켜져 있으면 최초 실제 발신 후보가 차단된다")
    void schedulerEnabledBlocksPreflight() {
        MockEnvironment environment = environment("dev")
                .withProperty("reservation.scheduler.enabled", "true");

        ClawOpsRealCallPreflightResponse response = service(
                readyProviderProperties(),
                configuredClawOpsProperties(),
                environment
        ).preflight(phoneRequest(TEST_PHONE_NUMBER));

        assertThat(response.realCallCandidate()).isFalse();
        assertThat(response.schedulerEnabled()).isTrue();
        assertThat(response.blockReasons()).contains("SCHEDULER_ENABLED");
    }

    @Test
    @DisplayName("모든 조건이 맞으면 특정 테스트 번호를 실제 발신 가능 후보로만 표시한다")
    void allConditionsPassForTestPhoneNumber() {
        ClawOpsRealCallPreflightResponse response = service(
                readyProviderProperties(),
                configuredClawOpsProperties(),
                environment("dev")
        ).preflight(phoneRequest(TEST_PHONE_NUMBER));

        assertThat(response.realCallCandidate()).isTrue();
        assertThat(response.blockReasons()).isEmpty();
        assertThat(response.warnings()).contains("RESERVATION_NOT_SELECTED");
        assertThat(response.targetType()).isEqualTo("PHONE_NUMBER");
        assertThat(response.targetPhoneNumberMasked()).isEqualTo("****5678");
        assertThat(response.providerMode()).isEqualTo("CLAWOPS");
        assertThat(response.providerRuntime()).isEqualTo("DIRECT_REST");
    }

    @Test
    @DisplayName("sidecar runtime에서는 Spring ClawOps API key 없이 sidecar 설정만으로 preflight를 통과할 수 있다")
    void sidecarRuntimeDoesNotRequireSpringClawOpsApiKey() {
        ReservationProviderProperties providerProperties = readyProviderProperties();
        providerProperties.setRuntime(ReservationProviderRuntime.CLAWOPS_SIDECAR);

        ClawOpsRealCallPreflightResponse response = service(
                providerProperties,
                configuredSidecarProperties(),
                environment("dev")
        ).preflight(phoneRequest(TEST_PHONE_NUMBER));

        assertThat(response.realCallCandidate()).isTrue();
        assertThat(response.providerRuntime()).isEqualTo("CLAWOPS_SIDECAR");
        assertThat(response.clawOpsConfigured()).isTrue();
        assertThat(response.missingClawOpsSettings()).isEmpty();
        assertThat(response.toString()).doesNotContain("CLAWOPS_API_KEY");
    }

    @Test
    @DisplayName("sidecar runtime에서는 sidecar base URL과 internal signing key가 없으면 preflight가 차단된다")
    void sidecarRuntimeRequiresSidecarSettings() {
        ReservationProviderProperties providerProperties = readyProviderProperties();
        providerProperties.setRuntime(ReservationProviderRuntime.CLAWOPS_SIDECAR);

        ClawOpsRealCallPreflightResponse response = service(
                providerProperties,
                new ClawOpsProperties(),
                environment("dev")
        ).preflight(phoneRequest(TEST_PHONE_NUMBER));

        assertThat(response.realCallCandidate()).isFalse();
        assertThat(response.providerRuntime()).isEqualTo("CLAWOPS_SIDECAR");
        assertThat(response.clawOpsConfigured()).isFalse();
        assertThat(response.blockReasons()).contains("CLAWOPS_REQUIRED_SETTINGS_MISSING");
        assertThat(response.missingClawOpsSettings()).containsExactly(
                "CLAWOPS_SIDECAR_BASE_URL",
                "CLAWOPS_SIDECAR_INTERNAL_SIGNING_KEY"
        );
        assertThat(response.missingClawOpsSettings()).doesNotContain("CLAWOPS_API_KEY");
    }

    @Test
    @DisplayName("sidecar readiness required가 켜져 있고 fake sidecar가 ready면 preflight를 통과할 수 있다")
    void sidecarReadinessRequiredCanPassWithReadyFakeSidecar() throws Exception {
        ReservationProviderProperties providerProperties = readyProviderProperties();
        providerProperties.setRuntime(ReservationProviderRuntime.CLAWOPS_SIDECAR);
        ClawOpsProperties clawOpsProperties = configuredSidecarProperties();
        clawOpsProperties.setSidecarReadinessRequired(true);

        try (FakeSidecarServer server = FakeSidecarServer.start(200, """
                {"ready":true,"profile":"dry-run","realCallEnabled":false,"allowlistCount":1,"clawOpsConfigured":false,"openAiConfigured":false,"agentRuntime":"dry-run","warnings":[]}
                """)) {
            clawOpsProperties.setSidecarBaseUrl(server.baseUrl());

            ClawOpsRealCallPreflightResponse response = service(
                    providerProperties,
                    clawOpsProperties,
                    environment("dev")
            ).preflight(phoneRequest(TEST_PHONE_NUMBER));

            assertThat(response.realCallCandidate()).isTrue();
            assertThat(response.blockReasons()).isEmpty();
            RecordedRequest readinessRequest = server.takeRequest();
            assertThat(readinessRequest.method()).isEqualTo("GET");
            assertThat(readinessRequest.path()).isEqualTo("/internal/clawops-agent/readiness");
            assertThat(ClawOpsSidecarInternalSignature.matches(
                    ClawOpsSidecarInternalSignature.sign(
                            TEST_SIDECAR_SIGNING_KEY,
                            readinessRequest.header(ClawOpsSidecarInternalSignature.TIMESTAMP_HEADER),
                            readinessRequest.body()
                    ),
                    readinessRequest.header(ClawOpsSidecarInternalSignature.SIGNATURE_HEADER)
            )).isTrue();
        }
    }

    @Test
    @DisplayName("sidecar readiness required에서 fake sidecar가 ready=false면 preflight를 차단한다")
    void sidecarReadinessNotReadyBlocksPreflight() throws Exception {
        ReservationProviderProperties providerProperties = readyProviderProperties();
        providerProperties.setRuntime(ReservationProviderRuntime.CLAWOPS_SIDECAR);
        ClawOpsProperties clawOpsProperties = configuredSidecarProperties();
        clawOpsProperties.setSidecarReadinessRequired(true);

        try (FakeSidecarServer server = FakeSidecarServer.start(200, """
                {"ready":false,"profile":"dry-run","realCallEnabled":false,"allowlistCount":0,"clawOpsConfigured":false,"openAiConfigured":false,"agentRuntime":"dry-run","warnings":["not-ready"]}
                """)) {
            clawOpsProperties.setSidecarBaseUrl(server.baseUrl());

            ClawOpsRealCallPreflightResponse response = service(
                    providerProperties,
                    clawOpsProperties,
                    environment("dev")
            ).preflight(phoneRequest(TEST_PHONE_NUMBER));

            assertThat(response.realCallCandidate()).isFalse();
            assertThat(response.blockReasons()).contains("CLAWOPS_SIDECAR_READINESS_NOT_READY");
        }
    }

    @Test
    @DisplayName("sidecar readiness HTTP 오류는 preflight 차단 사유로 남는다")
    void sidecarReadinessHttpErrorBlocksPreflight() throws Exception {
        ReservationProviderProperties providerProperties = readyProviderProperties();
        providerProperties.setRuntime(ReservationProviderRuntime.CLAWOPS_SIDECAR);
        ClawOpsProperties clawOpsProperties = configuredSidecarProperties();
        clawOpsProperties.setSidecarReadinessRequired(true);

        try (FakeSidecarServer server = FakeSidecarServer.start(503, """
                {"message":"not ready","secret":"hidden","phone":"01000000000"}
                """)) {
            clawOpsProperties.setSidecarBaseUrl(server.baseUrl());

            ClawOpsRealCallPreflightResponse response = service(
                    providerProperties,
                    clawOpsProperties,
                    environment("dev")
            ).preflight(phoneRequest(TEST_PHONE_NUMBER));

            assertThat(response.realCallCandidate()).isFalse();
            assertThat(response.blockReasons()).contains("CLAWOPS_SIDECAR_READINESS_HTTP_ERROR");
        }
    }

    @Test
    @DisplayName("sidecar base-url이 local이 아니면 readiness 호출 없이 preflight를 차단한다")
    void nonLocalSidecarBaseUrlBlocksPreflight() {
        ReservationProviderProperties providerProperties = readyProviderProperties();
        providerProperties.setRuntime(ReservationProviderRuntime.CLAWOPS_SIDECAR);
        ClawOpsProperties clawOpsProperties = configuredSidecarProperties();
        clawOpsProperties.setSidecarBaseUrl("https://sidecar.example.test");
        clawOpsProperties.setSidecarReadinessRequired(true);

        ClawOpsRealCallPreflightResponse response = service(
                providerProperties,
                clawOpsProperties,
                environment("dev")
        ).preflight(phoneRequest(TEST_PHONE_NUMBER));

        assertThat(response.realCallCandidate()).isFalse();
        assertThat(response.blockReasons()).contains("CLAWOPS_SIDECAR_HTTP_CONTRACT_DISABLED");
    }

    @Test
    @DisplayName("sidecar readiness timeout은 preflight 차단 사유로 남는다")
    void sidecarReadinessTimeoutBlocksPreflight() throws Exception {
        ReservationProviderProperties providerProperties = readyProviderProperties();
        providerProperties.setRuntime(ReservationProviderRuntime.CLAWOPS_SIDECAR);
        ClawOpsProperties clawOpsProperties = configuredSidecarProperties();
        clawOpsProperties.setSidecarReadinessRequired(true);
        clawOpsProperties.setSidecarReadTimeoutSeconds(1);

        try (FakeSidecarServer server = FakeSidecarServer.start(200, """
                {"ready":true}
                """, 1_500)) {
            clawOpsProperties.setSidecarBaseUrl(server.baseUrl());

            ClawOpsRealCallPreflightResponse response = service(
                    providerProperties,
                    clawOpsProperties,
                    environment("dev")
            ).preflight(phoneRequest(TEST_PHONE_NUMBER));

            assertThat(response.realCallCandidate()).isFalse();
            assertThat(response.blockReasons()).contains("CLAWOPS_SIDECAR_READINESS_TIMEOUT");
        }
    }

    @Test
    @DisplayName("모든 조건이 맞으면 특정 예약을 테스트 예약 후보로 확인한다")
    void allConditionsPassForReservation() {
        RestaurantReservation reservation = reservation(100L, TEST_PHONE_NUMBER);
        given(reservationRepository.findById(100L)).willReturn(Optional.of(reservation));

        ClawOpsRealCallPreflightResponse response = service(
                readyProviderProperties(),
                configuredClawOpsProperties(),
                environment("dev")
        ).preflight(new ClawOpsRealCallPreflightRequest(100L, TEST_PHONE_NUMBER));

        assertThat(response.realCallCandidate()).isTrue();
        assertThat(response.reservationId()).isEqualTo(100L);
        assertThat(response.restaurantId()).isEqualTo(10L);
        assertThat(response.reservationStatus()).isEqualTo(ReservationStatus.REQUESTED.name());
        assertThat(response.reservationTargetVerified()).isTrue();
        assertThat(response.targetPhoneNumberMasked()).isEqualTo("****5678");
    }

    @Test
    @DisplayName("preflight 응답에는 ClawOps secret 원문이 노출되지 않는다")
    void preflightResponseDoesNotExposeSecrets() {
        ClawOpsProperties clawOpsProperties = configuredClawOpsProperties();
        clawOpsProperties.setApiKey("super-secret-clawops-api-key");
        clawOpsProperties.setWebhookSigningKey("super-secret-webhook-signing-key");

        ClawOpsRealCallPreflightResponse response = service(
                readyProviderProperties(),
                clawOpsProperties,
                environment("dev")
        ).preflight(phoneRequest(TEST_PHONE_NUMBER));

        assertThat(response.realCallCandidate()).isTrue();
        assertThat(response.toString())
                .doesNotContain("super-secret-clawops-api-key")
                .doesNotContain("super-secret-webhook-signing-key")
                .doesNotContain(TEST_PHONE_NUMBER);
    }

    @Test
    @DisplayName("override preflight 응답에도 원본 번호와 override 번호 원문이 노출되지 않는다")
    void devTargetPhoneOverridePreflightResponseDoesNotExposeRawPhoneNumbers() {
        String databasePhoneNumber = "01000000000";
        ReservationProviderProperties providerProperties = readyProviderPropertiesWithOverride(TEST_PHONE_NUMBER);
        RestaurantReservation reservation = reservation(106L, databasePhoneNumber);
        given(reservationRepository.findById(106L)).willReturn(Optional.of(reservation));

        ClawOpsRealCallPreflightResponse response = service(
                providerProperties,
                configuredClawOpsProperties(),
                environment("dev")
        ).preflight(new ClawOpsRealCallPreflightRequest(106L, null));

        assertThat(response.realCallCandidate()).isTrue();
        assertThat(response.toString())
                .doesNotContain(databasePhoneNumber)
                .doesNotContain(TEST_PHONE_NUMBER);
    }

    private ClawOpsRealCallPreflightService service(
            ReservationProviderProperties providerProperties,
            ClawOpsProperties clawOpsProperties,
            MockEnvironment environment
    ) {
        return new ClawOpsRealCallPreflightService(
                reservationRepository,
                providerProperties,
                clawOpsProperties,
                new ClawOpsSidecarClient(clawOpsProperties, WebClient.builder(), new ObjectMapper()),
                environment
        );
    }

    private ClawOpsRealCallPreflightRequest phoneRequest(String phoneNumber) {
        return new ClawOpsRealCallPreflightRequest(null, phoneNumber);
    }

    private MockEnvironment environment(String... activeProfiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfiles);
        return environment;
    }

    private ReservationProviderProperties readyProviderProperties() {
        ReservationProviderProperties properties = new ReservationProviderProperties();
        properties.setMode(ReservationProviderMode.CLAWOPS);
        properties.setCallingEnabled(true);
        properties.setRealCallEnabled(true);
        properties.setCallAllowlistEnabled(true);
        properties.setRequireAllowlistForExternalCall(true);
        properties.setProdExternalCallBlocked(true);
        properties.setExternalCallAllowedProfiles(List.of("dev"));
        properties.setCallAllowedNumbers(List.of(TEST_PHONE_NUMBER));
        return properties;
    }

    private ReservationProviderProperties readyProviderPropertiesWithOverride(String overridePhoneNumber) {
        ReservationProviderProperties properties = readyProviderProperties();
        properties.setDevTargetPhoneOverrideEnabled(true);
        properties.setDevTargetPhoneOverrideNumber(overridePhoneNumber);
        return properties;
    }

    private ClawOpsProperties configuredClawOpsProperties() {
        ClawOpsProperties properties = new ClawOpsProperties();
        properties.setApiKey("test-clawops-api-key");
        properties.setAccountId("AC-test");
        properties.setFromNumber("07012345678");
        properties.setStatusCallbackUrl("https://example.test/webhooks/clawops");
        properties.setWebhookSigningKey("test-signing-key");
        return properties;
    }

    private ClawOpsProperties configuredSidecarProperties() {
        ClawOpsProperties properties = new ClawOpsProperties();
        properties.setSidecarBaseUrl("http://localhost:18080");
        properties.setSidecarInternalSigningKey(TEST_SIDECAR_SIGNING_KEY);
        return properties;
    }

    private RestaurantReservation reservation(Long id, String phoneNumber) {
        User user = User.builder()
                .provider("KAKAO")
                .providerUserId("provider-1")
                .nickname("user-1")
                .profileImageUrl("profile")
                .role(User.Role.USER)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);

        Restaurant restaurant = Restaurant.builder()
                .name("Restaurant 10")
                .address("Address 10")
                .roadAddress("Road 10")
                .categoryName("Korean")
                .regionName("Seoul")
                .lat(new BigDecimal("37.0"))
                .lng(new BigDecimal("127.0"))
                .phoneNumber(phoneNumber)
                .build();
        ReflectionTestUtils.setField(restaurant, "id", 10L);

        RestaurantReservation reservation = RestaurantReservation.builder()
                .user(user)
                .restaurant(restaurant)
                .reservationDateTime(LocalDateTime.now().plusDays(1))
                .partySize(4)
                .requestNote("preflight test")
                .restaurantPhoneNumberSnapshot(phoneNumber)
                .build();
        ReflectionTestUtils.setField(reservation, "id", id);
        return reservation;
    }

    private record RecordedRequest(
            String method,
            String path,
            com.sun.net.httpserver.Headers headers,
            String body
    ) {
        private String header(String name) {
            return headers.getFirst(name);
        }
    }

    private static class FakeSidecarServer implements AutoCloseable {

        private final HttpServer server;
        private final BlockingQueue<RecordedRequest> requests = new LinkedBlockingQueue<>();
        private final int responseStatus;
        private final byte[] responseBody;
        private final long responseDelayMillis;

        private FakeSidecarServer(
                HttpServer server,
                int responseStatus,
                String responseBody,
                long responseDelayMillis
        ) {
            this.server = server;
            this.responseStatus = responseStatus;
            this.responseBody = responseBody.getBytes(StandardCharsets.UTF_8);
            this.responseDelayMillis = responseDelayMillis;
        }

        private static FakeSidecarServer start(int responseStatus, String responseBody) throws IOException {
            return start(responseStatus, responseBody, 0);
        }

        private static FakeSidecarServer start(
                int responseStatus,
                String responseBody,
                long responseDelayMillis
        ) throws IOException {
            HttpServer httpServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            FakeSidecarServer fakeServer = new FakeSidecarServer(
                    httpServer,
                    responseStatus,
                    responseBody,
                    responseDelayMillis
            );
            httpServer.createContext("/", fakeServer::handle);
            httpServer.start();
            return fakeServer;
        }

        private String baseUrl() {
            return "http://localhost:" + server.getAddress().getPort();
        }

        private RecordedRequest takeRequest() throws InterruptedException {
            return requests.poll(2, TimeUnit.SECONDS);
        }

        private void handle(HttpExchange exchange) throws IOException {
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            requests.add(new RecordedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders(),
                    new String(requestBody, StandardCharsets.UTF_8)
            ));
            if (responseDelayMillis > 0) {
                try {
                    Thread.sleep(responseDelayMillis);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
            exchange.getResponseHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            exchange.sendResponseHeaders(responseStatus, responseBody.length);
            exchange.getResponseBody().write(responseBody);
            exchange.close();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
