package com.example.Capstone.client.reservation;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeoutException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ClawOpsSidecarClient {

    private static final String READINESS_METHOD = "GET";
    private static final String READINESS_PATH = "/internal/clawops-agent/readiness";
    private static final String CALL_METHOD = "POST";
    private static final String CALL_PATH = "/internal/clawops-agent/calls";

    private final ClawOpsProperties clawOpsProperties;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    public ClawOpsSidecarCallResponse dispatchDryRun(ClawOpsSidecarCallRequest request) {
        assertLocalSidecar();
        String rawBody = writeJson(request);
        String timestamp = Instant.now().toString();
        try {
            return webClient()
                    .post()
                    .uri(CALL_PATH)
                    .header(ClawOpsSidecarInternalSignature.TIMESTAMP_HEADER, timestamp)
                    .header(
                            ClawOpsSidecarInternalSignature.SIGNATURE_HEADER,
                            ClawOpsSidecarInternalSignature.sign(
                                    clawOpsProperties.getSidecarInternalSigningKey(),
                                    timestamp,
                                    rawBody
                            )
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(rawBody)
                    .retrieve()
                    .bodyToMono(ClawOpsSidecarCallResponse.class)
                    .block(sidecarTimeout());
        } catch (WebClientResponseException exception) {
            throw httpError(CALL_METHOD, CALL_PATH, exception);
        } catch (WebClientRequestException exception) {
            throw requestError(CALL_METHOD, CALL_PATH, exception);
        } catch (RuntimeException exception) {
            throw runtimeError(CALL_METHOD, CALL_PATH, exception);
        }
    }

    public ClawOpsSidecarReadinessCheckResult checkReadiness() {
        if (!clawOpsProperties.isSidecarConfigured()) {
            return ClawOpsSidecarReadinessCheckResult.blocked(
                    "CLAWOPS_SIDECAR_CONFIGURATION_MISSING",
                    "ClawOps sidecar base URL 또는 internal signing key가 없습니다."
            );
        }
        if (!clawOpsProperties.isLocalSidecarBaseUrl()) {
            return ClawOpsSidecarReadinessCheckResult.blocked(
                    "CLAWOPS_SIDECAR_HTTP_CONTRACT_DISABLED",
                    "ClawOps sidecar dry-run HTTP contract는 local endpoint에서만 호출합니다."
            );
        }

        String timestamp = Instant.now().toString();
        String rawBody = "";
        try {
            ClawOpsSidecarReadinessResponse response = webClient()
                    .get()
                    .uri(READINESS_PATH)
                    .header(ClawOpsSidecarInternalSignature.TIMESTAMP_HEADER, timestamp)
                    .header(
                            ClawOpsSidecarInternalSignature.SIGNATURE_HEADER,
                            ClawOpsSidecarInternalSignature.sign(
                                    clawOpsProperties.getSidecarInternalSigningKey(),
                                    timestamp,
                                    rawBody
                            )
                    )
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(ClawOpsSidecarReadinessResponse.class)
                    .block(sidecarTimeout());
            if (response == null || !response.isReady()) {
                return ClawOpsSidecarReadinessCheckResult.blocked(
                        "CLAWOPS_SIDECAR_READINESS_NOT_READY",
                        "ClawOps sidecar readiness가 ready=false 입니다."
                );
            }
            return ClawOpsSidecarReadinessCheckResult.ready(response);
        } catch (ClawOpsSidecarClientException exception) {
            return ClawOpsSidecarReadinessCheckResult.blocked(readinessBlockReason(exception), safeFailureMessage(exception));
        } catch (WebClientResponseException exception) {
            ClawOpsSidecarClientException mapped = httpError(READINESS_METHOD, READINESS_PATH, exception);
            return ClawOpsSidecarReadinessCheckResult.blocked(readinessBlockReason(mapped), safeFailureMessage(mapped));
        } catch (WebClientRequestException exception) {
            ClawOpsSidecarClientException mapped = requestError(READINESS_METHOD, READINESS_PATH, exception);
            return ClawOpsSidecarReadinessCheckResult.blocked(readinessBlockReason(mapped), safeFailureMessage(mapped));
        } catch (RuntimeException exception) {
            ClawOpsSidecarClientException mapped = runtimeError(READINESS_METHOD, READINESS_PATH, exception);
            return ClawOpsSidecarReadinessCheckResult.blocked(readinessBlockReason(mapped), safeFailureMessage(mapped));
        }
    }

    private WebClient webClient() {
        return webClientBuilder
                .baseUrl(clawOpsProperties.getSidecarBaseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private void assertLocalSidecar() {
        if (!clawOpsProperties.isLocalSidecarBaseUrl()) {
            throw new IllegalStateException("ClawOps sidecar dry-run HTTP contract는 local endpoint에서만 호출합니다.");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("sidecar request body를 JSON으로 변환할 수 없습니다.", exception);
        }
    }

    private Duration sidecarTimeout() {
        return Duration.ofSeconds(Math.max(1, clawOpsProperties.getSidecarReadTimeoutSeconds()));
    }

    private ClawOpsSidecarClientException httpError(
            String method,
            String path,
            WebClientResponseException exception
    ) {
        return ClawOpsSidecarClientException.httpError(
                exception.getStatusCode().value(),
                method,
                path,
                ClawOpsSafeLogSanitizer.sanitizeResponseBody(exception.getResponseBodyAsString()),
                exception
        );
    }

    private ClawOpsSidecarClientException requestError(
            String method,
            String path,
            WebClientRequestException exception
    ) {
        if (isTimeout(exception)) {
            return ClawOpsSidecarClientException.timeout(method, path, exception);
        }
        return ClawOpsSidecarClientException.networkError(method, path, exception);
    }

    private ClawOpsSidecarClientException runtimeError(String method, String path, RuntimeException exception) {
        if (isTimeout(exception)) {
            return ClawOpsSidecarClientException.timeout(method, path, exception);
        }
        if (exception instanceof ClawOpsSidecarClientException mapped) {
            return mapped;
        }
        return ClawOpsSidecarClientException.networkError(method, path, exception);
    }

    private String readinessBlockReason(ClawOpsSidecarClientException exception) {
        return switch (exception.getErrorType()) {
            case HTTP_ERROR -> "CLAWOPS_SIDECAR_READINESS_HTTP_ERROR";
            case TIMEOUT -> "CLAWOPS_SIDECAR_READINESS_TIMEOUT";
            case NETWORK_ERROR -> "CLAWOPS_SIDECAR_READINESS_NETWORK_ERROR";
        };
    }

    private String safeFailureMessage(ClawOpsSidecarClientException exception) {
        StringBuilder builder = new StringBuilder();
        builder.append("type=").append(exception.getErrorType());
        if (exception.getHttpStatusCode() != null) {
            builder.append("; httpStatus=").append(exception.getHttpStatusCode());
        }
        builder.append("; method=").append(exception.getMethod());
        builder.append("; path=").append(exception.getPath());
        if (exception.getResponseBodySummary() != null && !exception.getResponseBodySummary().isBlank()) {
            builder.append("; body=").append(exception.getResponseBodySummary());
        }
        return builder.toString();
    }

    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof TimeoutException) {
                return true;
            }
            String simpleName = current.getClass().getSimpleName().toLowerCase();
            String message = current.getMessage() == null ? "" : current.getMessage().toLowerCase();
            if (simpleName.contains("timeout") || message.contains("timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
