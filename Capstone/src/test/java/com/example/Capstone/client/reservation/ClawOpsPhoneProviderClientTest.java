package com.example.Capstone.client.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.reactive.function.client.WebClient;

import com.example.Capstone.domain.ReservationStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

@ExtendWith(MockitoExtension.class)
class ClawOpsPhoneProviderClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ClawOpsRestClient clawOpsRestClient;

    @Test
    @DisplayName("ClawOps base-url이 fake server가 아니면 HTTP 요청을 보내지 않는다")
    void nonLocalBaseUrlReturnsNoopWithoutHttp() {
        ClawOpsProperties properties = configuredProperties();
        ClawOpsPhoneProviderClient client = new ClawOpsPhoneProviderClient(
                properties,
                clawOpsRestClient,
                policy(realEndpointProperties(), properties, "dev")
        );

        PhoneProviderCallStartResult result = client.startCall(command());

        assertThat(result.started()).isFalse();
        assertThat(result.provider()).isEqualTo("NOOP");
        assertThat(result.providerStatus()).isEqualTo("NOOP_CLAWOPS_REAL_CALL_DISABLED");
        then(clawOpsRestClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("real-call-enabled가 true이고 모든 조건이 맞으면 실제 endpoint 후보가 활성화된다")
    void realEndpointCandidateIsEnabledOnlyWhenAllGuardsPass() {
        ClawOpsProperties properties = configuredProperties();
        ReservationProviderProperties providerProperties = realEndpointProperties();
        providerProperties.setRealCallEnabled(true);
        ClawOpsPhoneProviderClient client = new ClawOpsPhoneProviderClient(
                properties,
                clawOpsRestClient,
                policy(providerProperties, properties, "dev")
        );
        given(clawOpsRestClient.createCall(any(ClawOpsCreateCallRequest.class), eq(true)))
                .willReturn(new ClawOpsCreateCallResponse("CA-real-candidate", "initiated"));

        PhoneProviderCallStartResult result = client.startCall(command());

        assertThat(result.started()).isTrue();
        assertThat(result.provider()).isEqualTo("CLAWOPS");
        assertThat(result.providerCallId()).isEqualTo("CA-real-candidate");
        assertThat(result.providerStatus()).isEqualTo("initiated");
        then(clawOpsRestClient).should().createCall(any(ClawOpsCreateCallRequest.class), eq(true));
    }

    @Test
    @DisplayName("prod profile에서는 real-call-enabled가 true여도 실제 endpoint 후보가 차단된다")
    void prodProfileBlocksRealEndpointCandidate() {
        ClawOpsProperties properties = configuredProperties();
        ReservationProviderProperties providerProperties = realEndpointProperties();
        providerProperties.setRealCallEnabled(true);
        ClawOpsPhoneProviderClient client = new ClawOpsPhoneProviderClient(
                properties,
                clawOpsRestClient,
                policy(providerProperties, properties, "prod")
        );

        PhoneProviderCallStartResult result = client.startCall(command());

        assertThat(result.started()).isFalse();
        assertThat(result.providerStatus()).isEqualTo("NOOP_PROD_PROFILE_BLOCKED");
        then(clawOpsRestClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("allowlist가 비어 있으면 실제 endpoint 후보가 차단된다")
    void emptyAllowlistBlocksRealEndpointCandidate() {
        ClawOpsProperties properties = configuredProperties();
        ReservationProviderProperties providerProperties = realEndpointProperties();
        providerProperties.setRealCallEnabled(true);
        providerProperties.setCallAllowedNumbers(List.of());
        ClawOpsPhoneProviderClient client = new ClawOpsPhoneProviderClient(
                properties,
                clawOpsRestClient,
                policy(providerProperties, properties, "dev")
        );

        PhoneProviderCallStartResult result = client.startCall(command());

        assertThat(result.started()).isFalse();
        assertThat(result.providerStatus()).isEqualTo("NOOP_CALL_ALLOWLIST_REQUIRED");
        then(clawOpsRestClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("allowlist에 없는 번호는 실제 endpoint 후보가 차단된다")
    void nonAllowlistedNumberBlocksRealEndpointCandidate() {
        ClawOpsProperties properties = configuredProperties();
        ReservationProviderProperties providerProperties = realEndpointProperties();
        providerProperties.setRealCallEnabled(true);
        ClawOpsPhoneProviderClient client = new ClawOpsPhoneProviderClient(
                properties,
                clawOpsRestClient,
                policy(providerProperties, properties, "dev")
        );

        PhoneProviderCallStartResult result = client.startCall(new PhoneProviderCallStartCommand(
                ReservationProviderMode.CLAWOPS,
                100L,
                10L,
                "예약식당",
                "+15550100002",
                LocalDateTime.of(2026, 6, 1, 19, 0),
                4,
                "창가 자리",
                null
        ));

        assertThat(result.started()).isFalse();
        assertThat(result.providerStatus()).isEqualTo("NOOP_CALL_NOT_ALLOWLISTED");
        then(clawOpsRestClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("ClawOps 필수 설정이 없으면 HTTP 요청을 보내지 않는다")
    void missingConfigurationReturnsNoopWithoutHttp() throws Exception {
        try (FakeClawOpsServer server = FakeClawOpsServer.start(200, "{}")) {
            ClawOpsProperties properties = configuredProperties();
            properties.setBaseUrl(server.baseUrl());
            properties.setApiKey(null);
            ClawOpsPhoneProviderClient client = client(properties);

            PhoneProviderCallStartResult result = client.startCall(command());

            assertThat(result.started()).isFalse();
            assertThat(result.providerStatus()).isEqualTo("NOOP_CLAWOPS_CONFIGURATION_MISSING");
            assertThat(server.pollRequest()).isNull();
        }
    }

    @Test
    @DisplayName("fake server로 ClawOps outbound call 요청 JSON과 header를 검증한다")
    void fakeServerReceivesExpectedOutboundCallRequest() throws Exception {
        try (FakeClawOpsServer server = FakeClawOpsServer.start(200, """
                {"call_id":"CA123","status":"initiated"}
                """)) {
            ClawOpsProperties properties = configuredProperties();
            properties.setBaseUrl(server.baseUrl());
            ClawOpsPhoneProviderClient client = client(properties);

            PhoneProviderCallStartResult result = client.startCall(command());
            RecordedRequest request = server.takeRequest();
            Map<String, Object> body = objectMapper.readValue(
                    request.body(),
                    new TypeReference<>() {
                    }
            );

            assertThat(result.started()).isTrue();
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.path()).isEqualTo("/v1/accounts/AC-test/calls");
            assertThat(request.header(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer test-clawops-api-key");
            assertThat(request.header(HttpHeaders.CONTENT_TYPE)).contains(MediaType.APPLICATION_JSON_VALUE);
            assertThat(request.header(HttpHeaders.ACCEPT)).contains(MediaType.APPLICATION_JSON_VALUE);
            assertThat(body).containsEntry("To", "+15550100001");
            assertThat(body).containsEntry("From", "+15550100003");
            assertThat(body).containsEntry("StatusCallback", "https://example.test/webhooks/clawops");
            assertThat(body).containsEntry("StatusCallbackEvent", "initiated ringing answered completed");
            assertThat(body).containsEntry("Timeout", 30);
        }
    }

    @Test
    @DisplayName("fake server 성공 응답은 ClawOps provider start 결과로 변환한다")
    void fakeServerSuccessResponseMapsToProviderResult() throws Exception {
        try (FakeClawOpsServer server = FakeClawOpsServer.start(200, """
                {"CallId":"CA456","CallStatus":"ringing"}
                """)) {
            ClawOpsProperties properties = configuredProperties();
            properties.setBaseUrl(server.baseUrl());
            ClawOpsPhoneProviderClient client = client(properties);

            PhoneProviderCallStartResult result = client.startCall(command());

            assertThat(result.started()).isTrue();
            assertThat(result.reservationStatus()).isEqualTo(ReservationStatus.CALLING);
            assertThat(result.provider()).isEqualTo("CLAWOPS");
            assertThat(result.providerCallId()).isEqualTo("CA456");
            assertThat(result.providerStatus()).isEqualTo("ringing");
            assertThat(server.takeRequest()).isNotNull();
        }
    }

    @Test
    @DisplayName("fake server 실패 응답은 예약 상태를 시작 상태로 오염시키지 않는다")
    void fakeServerErrorResponseMapsToNoopResult() throws Exception {
        try (FakeClawOpsServer server = FakeClawOpsServer.start(500, """
                {"message":"provider failure","api_key":"leaked-api-key","phone":"+15550100001","Authorization":"Bearer token.part.signature"}
                """)) {
            ClawOpsProperties properties = configuredProperties();
            properties.setBaseUrl(server.baseUrl());
            ClawOpsPhoneProviderClient client = client(properties);

            PhoneProviderCallStartResult result = client.startCall(command());

            assertThat(result.started()).isFalse();
            assertThat(result.reservationStatus()).isEqualTo(ReservationStatus.REQUESTED);
            assertThat(result.provider()).isEqualTo("NOOP");
            assertThat(result.providerStatus()).isEqualTo("NOOP_CLAWOPS_HTTP_ERROR_500");
            assertThat(result.message())
                    .contains("type=HTTP_ERROR")
                    .contains("httpStatus=500")
                    .contains("path=/v1/accounts/{accountId}/calls")
                    .contains("[REDACTED]")
                    .doesNotContain("leaked-api-key")
                    .doesNotContain("+15550100001")
                    .doesNotContain("token.part.signature")
                    .doesNotContain("test-clawops-api-key");
            assertThat(server.takeRequest()).isNotNull();
        }
    }

    @Test
    @DisplayName("fake server 4xx/5xx HTTP status는 secret과 전화번호를 마스킹한 요약으로 구분한다")
    void fakeServerHttpErrorsReturnSafeStatusAndBodySummary() throws Exception {
        for (int status : List.of(400, 401, 403, 404, 500)) {
            try (FakeClawOpsServer server = FakeClawOpsServer.start(status, """
                    {
                      "message": "call rejected",
                      "apiKey": "super-secret-api-key",
                      "token": "aaa.bbbbbbbbbb.cccccccccc",
                      "to": "+15550100001",
                      "from": "+15550100003",
                      "nested": {
                        "phoneNumber": "+15550100001",
                        "detail": "Authorization: Bearer secret-token-value"
                      }
                    }
                    """)) {
                ClawOpsProperties properties = configuredProperties();
                properties.setBaseUrl(server.baseUrl());
                ClawOpsPhoneProviderClient client = client(properties);

                PhoneProviderCallStartResult result = client.startCall(command());

                assertThat(result.started()).isFalse();
                assertThat(result.providerStatus()).isEqualTo("NOOP_CLAWOPS_HTTP_ERROR_" + status);
                assertThat(result.message())
                        .contains("type=HTTP_ERROR")
                        .contains("httpStatus=" + status)
                        .contains("method=POST")
                        .contains("path=/v1/accounts/{accountId}/calls")
                        .contains("[REDACTED]")
                        .doesNotContain("super-secret-api-key")
                        .doesNotContain("aaa.bbbbbbbbbb.cccccccccc")
                        .doesNotContain("+15550100001")
                        .doesNotContain("+15550100001")
                        .doesNotContain("+15550100003")
                        .doesNotContain("secret-token-value")
                        .doesNotContain("test-clawops-api-key");
                assertThat(server.takeRequest()).isNotNull();
            }
        }
    }

    @Test
    @DisplayName("JSON이 아닌 ClawOps 오류 응답도 길이를 제한하고 민감 값을 마스킹한다")
    void nonJsonErrorBodyIsSanitizedAndLimited() throws Exception {
        String longBody = "error token=plain-token api_key=plain-api-key phone=+15550100001 "
                + "x".repeat(800);
        try (FakeClawOpsServer server = FakeClawOpsServer.start(400, longBody)) {
            ClawOpsProperties properties = configuredProperties();
            properties.setBaseUrl(server.baseUrl());
            ClawOpsPhoneProviderClient client = client(properties);

            PhoneProviderCallStartResult result = client.startCall(command());

            assertThat(result.providerStatus()).isEqualTo("NOOP_CLAWOPS_HTTP_ERROR_400");
            assertThat(result.message())
                    .contains("...[truncated]")
                    .doesNotContain("plain-token")
                    .doesNotContain("plain-api-key")
                    .doesNotContain("+15550100001");
            assertThat(result.message().length()).isLessThan(760);
        }
    }

    @Test
    @DisplayName("ClawOps network error는 HTTP status 없는 network error로 구분한다")
    void networkErrorIsSeparatedFromHttpError() throws Exception {
        ClawOpsProperties properties = configuredProperties();
        properties.setBaseUrl("http://localhost:" + unusedLocalPort());
        ClawOpsPhoneProviderClient client = client(properties);

        PhoneProviderCallStartResult result = client.startCall(command());

        assertThat(result.started()).isFalse();
        assertThat(result.providerStatus()).isEqualTo("NOOP_CLAWOPS_NETWORK_ERROR");
        assertThat(result.message())
                .contains("type=NETWORK_ERROR")
                .doesNotContain("httpStatus=")
                .doesNotContain("+15550100001")
                .doesNotContain("test-clawops-api-key");
    }

    @Test
    @DisplayName("ClawOps timeout은 HTTP error와 별도 상태로 구분한다")
    void timeoutIsSeparatedFromHttpError() throws Exception {
        try (FakeClawOpsServer server = FakeClawOpsServer.start(200, """
                {"call_id":"CA-late","status":"initiated"}
                """, 1_500)) {
            ClawOpsProperties properties = configuredProperties();
            properties.setBaseUrl(server.baseUrl());
            properties.setCallTimeoutSeconds(1);
            ClawOpsPhoneProviderClient client = client(properties);

            PhoneProviderCallStartResult result = client.startCall(command());

            assertThat(result.started()).isFalse();
            assertThat(result.providerStatus()).isEqualTo("NOOP_CLAWOPS_TIMEOUT");
            assertThat(result.message())
                    .contains("type=TIMEOUT")
                    .doesNotContain("+15550100001")
                    .doesNotContain("test-clawops-api-key");
        }
    }

    private ClawOpsPhoneProviderClient client(ClawOpsProperties properties) {
        return new ClawOpsPhoneProviderClient(
                properties,
                new ClawOpsRestClient(properties, WebClient.builder()),
                policy(realEndpointProperties(), properties, "dev")
        );
    }

    private ClawOpsEndpointAccessPolicy policy(
            ReservationProviderProperties providerProperties,
            ClawOpsProperties clawOpsProperties,
            String activeProfile
    ) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfile);
        return new ClawOpsEndpointAccessPolicy(providerProperties, clawOpsProperties, environment);
    }

    private ReservationProviderProperties realEndpointProperties() {
        ReservationProviderProperties properties = new ReservationProviderProperties();
        properties.setMode(ReservationProviderMode.CLAWOPS);
        properties.setCallingEnabled(true);
        properties.setCallAllowlistEnabled(true);
        properties.setRequireAllowlistForExternalCall(true);
        properties.setProdExternalCallBlocked(true);
        properties.setExternalCallAllowedProfiles(List.of("dev"));
        properties.setCallAllowedNumbers(List.of("+15550100001"));
        return properties;
    }

    private ClawOpsProperties configuredProperties() {
        ClawOpsProperties properties = new ClawOpsProperties();
        properties.setApiKey("test-clawops-api-key");
        properties.setAccountId("AC-test");
        properties.setFromNumber("+15550100003");
        properties.setStatusCallbackUrl("https://example.test/webhooks/clawops");
        properties.setWebhookSigningKey("test-signing-key");
        return properties;
    }

    private PhoneProviderCallStartCommand command() {
        return new PhoneProviderCallStartCommand(
                ReservationProviderMode.CLAWOPS,
                100L,
                10L,
                "예약식당",
                "+15550100001",
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

    private static class FakeClawOpsServer implements AutoCloseable {

        private final HttpServer server;
        private final BlockingQueue<RecordedRequest> requests = new LinkedBlockingQueue<>();
        private final int responseStatus;
        private final byte[] responseBody;
        private final long responseDelayMillis;

        private FakeClawOpsServer(HttpServer server, int responseStatus, String responseBody) {
            this(server, responseStatus, responseBody, 0);
        }

        private FakeClawOpsServer(
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

        private static FakeClawOpsServer start(int responseStatus, String responseBody) throws IOException {
            return start(responseStatus, responseBody, 0);
        }

        private static FakeClawOpsServer start(
                int responseStatus,
                String responseBody,
                long responseDelayMillis
        ) throws IOException {
            HttpServer httpServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            FakeClawOpsServer fakeServer = new FakeClawOpsServer(
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

        private RecordedRequest pollRequest() {
            return requests.poll();
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
