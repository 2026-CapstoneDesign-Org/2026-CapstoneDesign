package com.example.Capstone.client.reservation;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ClawOpsRestClient {

    private static final String CREATE_CALL_METHOD = "POST";
    private static final String CREATE_CALL_PATH_TEMPLATE = "/v1/accounts/{accountId}/calls";

    private final ClawOpsProperties clawOpsProperties;
    private final WebClient.Builder webClientBuilder;

    public ClawOpsCreateCallResponse createCall(ClawOpsCreateCallRequest request) {
        return createCall(request, false);
    }

    public ClawOpsCreateCallResponse createCall(
            ClawOpsCreateCallRequest request,
            boolean realEndpointAllowed
    ) {
        if (!clawOpsProperties.isLocalHttpContractBaseUrl() && !realEndpointAllowed) {
            throw new IllegalStateException("ClawOps REST client는 fake server 또는 dev 승인된 실제 endpoint에서만 사용할 수 있습니다.");
        }

        try {
            return webClientBuilder
                    .baseUrl(clawOpsProperties.getBaseUrl())
                    .build()
                    .post()
                    .uri(CREATE_CALL_PATH_TEMPLATE, clawOpsProperties.getAccountId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + clawOpsProperties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(ClawOpsCreateCallResponse.class)
                    .block(Duration.ofSeconds(Math.max(1, clawOpsProperties.getCallTimeoutSeconds())));
        } catch (WebClientResponseException exception) {
            throw ClawOpsRestClientException.httpError(
                    exception.getStatusCode().value(),
                    CREATE_CALL_METHOD,
                    CREATE_CALL_PATH_TEMPLATE,
                    ClawOpsSafeLogSanitizer.sanitizeResponseBody(exception.getResponseBodyAsString()),
                    exception
            );
        } catch (WebClientRequestException exception) {
            if (isTimeout(exception)) {
                throw ClawOpsRestClientException.timeout(CREATE_CALL_METHOD, CREATE_CALL_PATH_TEMPLATE, exception);
            }
            throw ClawOpsRestClientException.networkError(CREATE_CALL_METHOD, CREATE_CALL_PATH_TEMPLATE, exception);
        } catch (RuntimeException exception) {
            if (isTimeout(exception)) {
                throw ClawOpsRestClientException.timeout(CREATE_CALL_METHOD, CREATE_CALL_PATH_TEMPLATE, exception);
            }
            throw exception;
        }
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
