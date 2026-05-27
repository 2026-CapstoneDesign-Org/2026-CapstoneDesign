package com.example.Capstone.client.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.reactive.function.client.WebClient;

import com.example.Capstone.domain.ReservationStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class ClawOpsSidecarPhoneProviderClientTest {

    private static final String PLACEHOLDER_PHONE_NUMBER = "+15550100001";
    private static final String SIGNING_KEY = "fake-sidecar-signing-key";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("fake sidecar는 dry-run 발신 요청 계약을 받지만 예약 시작 상태로 오염시키지 않는다")
    void fakeSidecarReceivesDryRunCallRequest() throws Exception {
        try (FakeSidecarServer server = FakeSidecarServer.start(200, """
                {
                  "accepted": true,
                  "provider": "CLAWOPS_SIDECAR",
                  "providerCallId": "dry-run-provider-call-100",
                  "providerStatus": "DRY_RUN_ACCEPTED",
                  "sidecarCallId": "dry-run-sidecar-100",
                  "idempotencyKey": "clawops-sidecar-dry-run:100",
                  "message": "dry-run accepted for ****0001",
                  "realAgentGatePreview": {
                    "runtimeMode": "dry-run",
                    "realAgentEnabled": false,
                    "approvalRequired": true,
                    "springPreflightRequired": true,
                    "allowed": false,
                    "blocked": true,
                    "blockReasons": ["REAL_AGENT_DISABLED", "APPROVAL_REQUIRED"],
                    "missingRequiredEnvNames": ["CLAWOPS_API_KEY", "OPENAI_API_KEY"],
                    "sdkInstalled": false
                  }
                }
                """)) {
            ClawOpsProperties properties = sidecarProperties();
            properties.setSidecarBaseUrl(server.baseUrl());
            ReservationProviderProperties providerProperties = sidecarProviderProperties();
            providerProperties.setRealCallEnabled(false);
            ClawOpsSidecarPhoneProviderClient client = client(providerProperties, properties, "dev");

            PhoneProviderCallStartResult result = client.startCall(command(PLACEHOLDER_PHONE_NUMBER));
            RecordedRequest request = server.takeRequest();
            Map<String, Object> body = objectMapper.readValue(request.body(), new TypeReference<>() {
            });

            assertThat(result.started()).isFalse();
            assertThat(result.reservationStatus()).isEqualTo(ReservationStatus.REQUESTED);
            assertThat(result.provider()).isEqualTo("NOOP");
            assertThat(result.providerStatus()).isEqualTo("NOOP_CLAWOPS_SIDECAR_DRY_RUN_ACCEPTED");
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.path()).isEqualTo("/internal/clawops-agent/calls");
            assertThat(request.header(HttpHeaders.CONTENT_TYPE)).contains(MediaType.APPLICATION_JSON_VALUE);
            assertThat(request.header(ClawOpsSidecarInternalSignature.TIMESTAMP_HEADER)).isNotBlank();
            assertThat(request.header(ClawOpsSidecarInternalSignature.SIGNATURE_HEADER)).isNotBlank();
            assertThat(ClawOpsSidecarInternalSignature.matches(
                    ClawOpsSidecarInternalSignature.sign(
                            SIGNING_KEY,
                            request.header(ClawOpsSidecarInternalSignature.TIMESTAMP_HEADER),
                            request.body()
                    ),
                    request.header(ClawOpsSidecarInternalSignature.SIGNATURE_HEADER)
            )).isTrue();
            assertThat(body).containsEntry("reservationId", 100);
            assertThat(body).containsEntry("callAttemptId", null);
            assertThat(body).containsEntry("idempotencyKey", "clawops-sidecar-dry-run:100");
            assertThat(body).containsEntry("targetPhoneNumber", PLACEHOLDER_PHONE_NUMBER);
            assertThat(body).containsEntry("restaurantName", "예약식당");
            assertThat(body).containsEntry("reservationDateTime", "2026-06-01T19:00");
            assertThat(body).containsEntry("partySize", 4);
            assertThat(body).containsEntry("requestNote", "창가 자리");
            assertThat(body).containsEntry("dryRun", true);
        }
    }

    @Test
    @DisplayName("sidecar base-url이 local이 아니면 HTTP 요청 없이 no-op으로 차단한다")
    void nonLocalSidecarBaseUrlReturnsNoopWithoutHttp() {
        ClawOpsProperties properties = sidecarProperties();
        properties.setSidecarBaseUrl("https://sidecar.example.test");
        ClawOpsSidecarPhoneProviderClient client = client(sidecarProviderProperties(), properties, "dev");

        PhoneProviderCallStartResult result = client.startCall(command(PLACEHOLDER_PHONE_NUMBER));

        assertThat(result.started()).isFalse();
        assertThat(result.providerStatus()).isEqualTo("NOOP_CLAWOPS_SIDECAR_HTTP_CONTRACT_DISABLED");
    }

    @Test
    @DisplayName("sidecar 설정 누락 시 no-op으로 차단한다")
    void missingSidecarSettingsReturnNoop() {
        ClawOpsSidecarPhoneProviderClient client = client(
                sidecarProviderProperties(),
                new ClawOpsProperties(),
                "dev"
        );

        PhoneProviderCallStartResult result = client.startCall(command(PLACEHOLDER_PHONE_NUMBER));

        assertThat(result.started()).isFalse();
        assertThat(result.providerStatus()).isEqualTo("NOOP_CLAWOPS_SIDECAR_CONFIGURATION_MISSING");
    }

    @Test
    @DisplayName("sidecar client도 prod profile에서는 차단한다")
    void prodProfileReturnsNoop() {
        ClawOpsSidecarPhoneProviderClient client = client(
                sidecarProviderProperties(),
                sidecarProperties(),
                "prod"
        );

        PhoneProviderCallStartResult result = client.startCall(command(PLACEHOLDER_PHONE_NUMBER));

        assertThat(result.started()).isFalse();
        assertThat(result.providerStatus()).isEqualTo("NOOP_PROD_PROFILE_BLOCKED");
    }

    @Test
    @DisplayName("sidecar client도 allowlist 밖 번호는 차단한다")
    void nonAllowlistedPhoneNumberReturnsNoop() {
        ClawOpsSidecarPhoneProviderClient client = client(
                sidecarProviderProperties(),
                sidecarProperties(),
                "dev"
        );

        PhoneProviderCallStartResult result = client.startCall(command("+15550100999"));

        assertThat(result.started()).isFalse();
        assertThat(result.providerStatus()).isEqualTo("NOOP_CALL_NOT_ALLOWLISTED");
    }

    @Test
    @DisplayName("fake sidecar HTTP 오류는 예약 상태를 오염시키지 않고 민감값을 마스킹한다")
    void fakeSidecarHttpErrorReturnsSafeNoop() throws Exception {
        try (FakeSidecarServer server = FakeSidecarServer.start(500, """
                {"message":"failed","phone":"+15550100001","token":"aaa.bbbbbbbbbb.cccccccccc","signing_key":"secret-value"}
                """)) {
            ClawOpsProperties properties = sidecarProperties();
            properties.setSidecarBaseUrl(server.baseUrl());
            ClawOpsSidecarPhoneProviderClient client = client(sidecarProviderProperties(), properties, "dev");

            PhoneProviderCallStartResult result = client.startCall(command(PLACEHOLDER_PHONE_NUMBER));

            assertThat(result.started()).isFalse();
            assertThat(result.reservationStatus()).isEqualTo(ReservationStatus.REQUESTED);
            assertThat(result.providerStatus()).isEqualTo("NOOP_CLAWOPS_SIDECAR_HTTP_ERROR_500");
            assertThat(result.message())
                    .contains("type=HTTP_ERROR")
                    .contains("httpStatus=500")
                    .contains("path=/internal/clawops-agent/calls")
                    .contains("[REDACTED]")
                    .doesNotContain("secret-value")
                    .doesNotContain("+15550100001")
                    .doesNotContain("aaa.bbbbbbbbbb.cccccccccc")
                    .doesNotContain(SIGNING_KEY);
            assertThat(server.takeRequest()).isNotNull();
        }
    }

    @Test
    @DisplayName("sidecar network error는 HTTP status 없는 no-op으로 구분한다")
    void networkErrorReturnsNoop() throws Exception {
        ClawOpsProperties properties = sidecarProperties();
        properties.setSidecarBaseUrl("http://localhost:" + unusedLocalPort());
        ClawOpsSidecarPhoneProviderClient client = client(sidecarProviderProperties(), properties, "dev");

        PhoneProviderCallStartResult result = client.startCall(command(PLACEHOLDER_PHONE_NUMBER));

        assertThat(result.started()).isFalse();
        assertThat(result.providerStatus()).isEqualTo("NOOP_CLAWOPS_SIDECAR_NETWORK_ERROR");
        assertThat(result.message())
                .contains("type=NETWORK_ERROR")
                .doesNotContain("httpStatus=")
                .doesNotContain(PLACEHOLDER_PHONE_NUMBER)
                .doesNotContain(SIGNING_KEY);
    }

    @Test
    @DisplayName("sidecar timeout은 HTTP error와 별도 no-op으로 구분한다")
    void timeoutReturnsNoop() throws Exception {
        try (FakeSidecarServer server = FakeSidecarServer.start(200, """
                {"accepted":true}
                """, 1_500)) {
            ClawOpsProperties properties = sidecarProperties();
            properties.setSidecarBaseUrl(server.baseUrl());
            properties.setSidecarReadTimeoutSeconds(1);
            ClawOpsSidecarPhoneProviderClient client = client(sidecarProviderProperties(), properties, "dev");

            PhoneProviderCallStartResult result = client.startCall(command(PLACEHOLDER_PHONE_NUMBER));

            assertThat(result.started()).isFalse();
            assertThat(result.providerStatus()).isEqualTo("NOOP_CLAWOPS_SIDECAR_TIMEOUT");
            assertThat(result.message())
                    .contains("type=TIMEOUT")
                    .doesNotContain(PLACEHOLDER_PHONE_NUMBER)
                    .doesNotContain(SIGNING_KEY);
        }
    }

    private ClawOpsSidecarPhoneProviderClient client(
            ReservationProviderProperties providerProperties,
            ClawOpsProperties clawOpsProperties,
            String activeProfile
    ) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfile);
        return new ClawOpsSidecarPhoneProviderClient(
                providerProperties,
                clawOpsProperties,
                new ClawOpsSidecarClient(clawOpsProperties, WebClient.builder(), objectMapper),
                environment
        );
    }

    private ReservationProviderProperties sidecarProviderProperties() {
        ReservationProviderProperties properties = new ReservationProviderProperties();
        properties.setMode(ReservationProviderMode.CLAWOPS);
        properties.setRuntime(ReservationProviderRuntime.CLAWOPS_SIDECAR);
        properties.setCallingEnabled(true);
        properties.setRealCallEnabled(true);
        properties.setCallAllowlistEnabled(true);
        properties.setRequireAllowlistForExternalCall(true);
        properties.setProdExternalCallBlocked(true);
        properties.setCallAllowedNumbers(List.of(PLACEHOLDER_PHONE_NUMBER));
        return properties;
    }

    private ClawOpsProperties sidecarProperties() {
        ClawOpsProperties properties = new ClawOpsProperties();
        properties.setSidecarBaseUrl("http://localhost:18080");
        properties.setSidecarInternalSigningKey(SIGNING_KEY);
        return properties;
    }

    private PhoneProviderCallStartCommand command(String phoneNumber) {
        return new PhoneProviderCallStartCommand(
                ReservationProviderMode.CLAWOPS,
                100L,
                10L,
                "예약식당",
                phoneNumber,
                LocalDateTime.of(2026, 6, 1, 19, 0),
                4,
                "창가 자리",
                null
        );
    }

    private int unusedLocalPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
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
